package com.practice.platform.web;

import com.practice.platform.ai.OllamaClient;
import com.practice.platform.dto.CodeRequest;
import com.practice.platform.dto.GenerateRequest;
import com.practice.platform.dto.NoteRequest;
import com.practice.platform.entity.Problem;
import com.practice.platform.entity.UserAccount;
import com.practice.platform.repo.AttemptRepository;
import com.practice.platform.repo.ProblemRepository;
import com.practice.platform.service.GenerationService;
import com.practice.platform.service.GenerationStreamer;
import com.practice.platform.service.PracticeService;
import com.practice.platform.service.SseTaskRunner;
import com.practice.platform.service.StatsService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ApiController {

    private final CurrentUser currentUser;
    private final GenerationService generation;
    private final GenerationStreamer generationStreamer;
    private final SseTaskRunner sse;
    private final PracticeService practice;
    private final StatsService stats;
    private final ProblemRepository problems;
    private final AttemptRepository attempts;
    private final OllamaClient ai;

    public ApiController(CurrentUser currentUser, GenerationService generation, GenerationStreamer generationStreamer,
                         SseTaskRunner sse, PracticeService practice, StatsService stats,
                         ProblemRepository problems, AttemptRepository attempts, OllamaClient ai) {
        this.currentUser = currentUser;
        this.generation = generation;
        this.generationStreamer = generationStreamer;
        this.sse = sse;
        this.practice = practice;
        this.stats = stats;
        this.problems = problems;
        this.attempts = attempts;
        this.ai = ai;
    }

    @GetMapping("/ai/health")
    public Map<String, Object> aiHealth() {
        currentUser.require();
        OllamaClient.AiHealth h = ai.health();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ollama", h.ollamaUp() ? "up" : "down");
        m.put("model", h.model());
        m.put("modelInstalled", h.modelInstalled());
        return m;
    }

    @GetMapping("/dashboard")
    public Map<String, Object> dashboard() {
        UserAccount u = currentUser.require();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("username", u.getUsername());
        m.put("email", u.getEmail());
        m.put("streak", u.getCurrentStreak());
        m.put("totalAccepted", u.getTotalAccepted());
        m.put("totalAttempts", u.getTotalAttempts());
        m.put("hintUsage", u.getHintUsage());
        m.put("suggestedTopic", u.getSuggestedTopic() == null ? "" : u.getSuggestedTopic());
        m.put("acceptRate", u.getTotalAttempts() == 0 ? 0
                : Math.round(1000.0 * u.getTotalAccepted() / u.getTotalAttempts()) / 10.0);
        m.put("model", generation.getModelName());
        m.put("problemCount", problems.findByOwnerOrderByCreatedAtDesc(u).size());
        m.put("activity", stats.lastSevenDays(u));
        return m;
    }

    /**
     * Streams generation progress as Server-Sent Events. The response is a stream of
     * {@code stage} events followed by a final {@code done} (with the new problem id) or
     * {@code error} event. Requests that fail validation still return a normal JSON error.
     */
    @PostMapping(value = "/problems/generate", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter generate(@RequestBody GenerateRequest req) {
        UserAccount u = currentUser.require();
        if (req.prompt == null || req.prompt.isBlank()) {
            throw new IllegalArgumentException("Describe the problem you want.");
        }
        return generationStreamer.stream(u, req.prompt.strip(), req.difficulty, req.topic);
    }

    @GetMapping("/problems")
    public Object list() {
        UserAccount u = currentUser.require();
        return problems.findByOwnerOrderByCreatedAtDesc(u).stream().map(p -> Map.of(
                "id", p.getId(),
                "title", p.getTitle(),
                "difficulty", p.getDifficulty() == null ? "" : p.getDifficulty(),
                "topicTags", p.getTopicTags() == null ? "" : p.getTopicTags(),
                "verified", p.isVerified(),
                "createdAt", p.getCreatedAt()
        )).toList();
    }

    @GetMapping("/problems/{id}")
    public Map<String, Object> get(@PathVariable Long id) {
        UserAccount u = currentUser.require();
        Problem p = practice.requireOwned(id, u);
        return practice.problemDto(p, u, true);
    }

    @GetMapping("/problems/{id}/attempts")
    public Object attempts(@PathVariable Long id) {
        UserAccount u = currentUser.require();
        Problem p = practice.requireOwned(id, u);
        return attempts.findByUserAndProblemOrderByCreatedAtDesc(u, p).stream().limit(30).map(a -> Map.of(
                "id", a.getId(),
                "language", a.getLanguage(),
                "verdict", a.getVerdict(),
                "submit", a.isSubmit(),
                "accepted", a.isAccepted(),
                "passed", a.getPassed(),
                "total", a.getTotal(),
                "createdAt", a.getCreatedAt()
        )).toList();
    }

    @PostMapping("/problems/{id}/run")
    public Map<String, Object> run(@PathVariable Long id, @RequestBody CodeRequest req) {
        return practice.run(currentUser.require(), id, req.language, req.code);
    }

    @PostMapping("/problems/{id}/submit")
    public Map<String, Object> submit(@PathVariable Long id, @RequestBody CodeRequest req) {
        return practice.submit(currentUser.require(), id, req.language, req.code);
    }

    /**
     * Streams hint generation: {@code stage} events while the model thinks, {@code delta}
     * events with the hint text as it arrives, then a final {@code done} or {@code error}.
     */
    @PostMapping(value = "/problems/{id}/hint", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter hint(@PathVariable Long id) {
        UserAccount u = currentUser.require();
        return sse.run(emitter -> {
            Map<String, Object> result = practice.requestHint(u, id,
                    SseTaskRunner.stages(emitter), SseTaskRunner.deltas(emitter));
            SseTaskRunner.send(emitter, "done", result);
        });
    }

    @PostMapping("/problems/{id}/retry")
    public Map<String, String> retry(@PathVariable Long id) {
        practice.retry(currentUser.require(), id);
        return Map.of("status", "ok");
    }

    @PostMapping("/problems/{id}/report")
    public Map<String, Object> report(@PathVariable Long id) {
        return practice.reportBadTests(currentUser.require(), id);
    }

    /**
     * Streams the post-accept review the same way as hints: stages, streamed text deltas,
     * then {@code done} with the complete review (or the cached copy).
     */
    @PostMapping(value = "/problems/{id}/review", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter review(@PathVariable Long id) {
        UserAccount u = currentUser.require();
        return sse.run(emitter -> {
            String review = practice.postAcceptReview(u, id,
                    SseTaskRunner.stages(emitter), SseTaskRunner.deltas(emitter));
            SseTaskRunner.send(emitter, "done", Map.of("review", review));
        });
    }

    @PutMapping("/problems/{id}/note")
    public Map<String, String> note(@PathVariable Long id, @RequestBody NoteRequest req) {
        practice.saveNote(currentUser.require(), id, req.content);
        return Map.of("status", "ok");
    }
}
