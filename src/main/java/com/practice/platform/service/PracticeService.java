package com.practice.platform.service;

import com.practice.platform.ai.OllamaClient;
import com.practice.platform.config.AppProperties;
import com.practice.platform.entity.*;
import com.practice.platform.judge.CaseResult;
import com.practice.platform.judge.JudgeService;
import com.practice.platform.judge.SuiteResult;
import com.practice.platform.repo.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Service
public class PracticeService {

    private final ProblemRepository problems;
    private final TestCaseRepository testCases;
    private final AttemptRepository attempts;
    private final HintRepository hints;
    private final NoteRepository notes;
    private final ProblemProgressRepository progressRepo;
    private final JudgeService judge;
    private final StatsService stats;
    private final OllamaClient ollama;
    private final LocalAiLock aiLock;
    private final AppProperties props;

    public PracticeService(ProblemRepository problems, TestCaseRepository testCases, AttemptRepository attempts,
                           HintRepository hints, NoteRepository notes, ProblemProgressRepository progressRepo,
                           JudgeService judge, StatsService stats, OllamaClient ollama, LocalAiLock aiLock,
                           AppProperties props) {
        this.problems = problems;
        this.testCases = testCases;
        this.attempts = attempts;
        this.hints = hints;
        this.notes = notes;
        this.progressRepo = progressRepo;
        this.judge = judge;
        this.stats = stats;
        this.ollama = ollama;
        this.aiLock = aiLock;
        this.props = props;
    }

    public Problem requireOwned(Long problemId, UserAccount user) {
        Problem p = problems.findWithOwnerById(problemId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Problem not found."));
        if (!p.getOwner().getId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not your problem.");
        }
        return p;
    }

    public Map<String, Object> problemDto(Problem p, UserAccount user, boolean includeReview) {
        List<TestCase> all = testCases.findByProblemOrderBySortOrderAsc(p);
        ProblemProgress prog = progressRepo.findByUserAndProblem(user, p).orElse(null);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", p.getId());
        m.put("title", p.getTitle());
        m.put("statement", p.getStatement());
        m.put("constraints", p.getConstraintsText());
        m.put("difficulty", p.getDifficulty());
        m.put("topicTags", p.getTopicTags());
        m.put("starterJava", p.getStarterJava());
        m.put("starterPython", p.getStarterPython());
        m.put("verified", p.isVerified());
        m.put("createdAt", p.getCreatedAt());
        m.put("publicTests", all.stream().filter(t -> !t.isHidden()).map(this::publicTestDto).toList());
        m.put("hiddenCount", all.stream().filter(TestCase::isHidden).count());
        m.put("progress", progressDto(prog));
        m.put("hints", hints.findByUserAndProblemOrderByLevelAsc(user, p).stream().map(h -> Map.of(
                "level", h.getLevel(),
                "text", h.getText()
        )).toList());
        boolean accepted = prog != null && prog.isAccepted();
        if (includeReview && accepted) {
            m.put("postAcceptReview", p.getPostAcceptReview());
        }
        notes.findByUserAndProblem(user, p).ifPresent(n -> m.put("note", n.getContent()));
        return m;
    }

    private Map<String, Object> publicTestDto(TestCase t) {
        return Map.of(
                "id", t.getId(),
                "input", t.getInputData(),
                "expected", t.getExpectedOutput()
        );
    }

    private Map<String, Object> progressDto(ProblemProgress prog) {
        Map<String, Object> m = new LinkedHashMap<>();
        int threshold = props.getHints().getFailThreshold();
        if (prog == null) {
            m.put("consecutiveFailedSubmits", 0);
            m.put("accepted", false);
            m.put("hintsUnlocked", 0);
            m.put("hintAvailable", false);
            m.put("failsUntilHint", threshold);
            m.put("lastCode", null);
            m.put("previousCode", null);
            m.put("lastLanguage", "python");
            return m;
        }
        m.put("consecutiveFailedSubmits", prog.getConsecutiveFailedSubmits());
        m.put("accepted", prog.isAccepted());
        m.put("hintsUnlocked", prog.getHintsUnlocked());
        m.put("hintAvailable", prog.getConsecutiveFailedSubmits() >= threshold && prog.getHintsUnlocked() < 3);
        m.put("failsUntilHint", Math.max(0, threshold - prog.getConsecutiveFailedSubmits()));
        m.put("lastCode", prog.getLastCode());
        m.put("previousCode", prog.getPreviousCode());
        m.put("lastLanguage", prog.getLastLanguage());
        return m;
    }

    @Transactional
    public Map<String, Object> run(UserAccount user, Long problemId, String language, String code) {
        Problem p = requireOwned(problemId, user);
        language = normalizeLang(language);
        List<TestCase> all = testCases.findByProblemOrderBySortOrderAsc(p);
        SuiteResult result = judge.runSuite(language, code, all, false);
        saveAttempt(user, p, language, code, result, false, result.allPassed());
        return suiteDto(result, false);
    }

    @Transactional
    public Map<String, Object> submit(UserAccount user, Long problemId, String language, String code) {
        Problem p = requireOwned(problemId, user);
        language = normalizeLang(language);
        List<TestCase> all = testCases.findByProblemOrderBySortOrderAsc(p);
        SuiteResult result = judge.runSuite(language, code, all, true);
        boolean accepted = result.allPassed();
        saveAttempt(user, p, language, code, result, true, accepted);
        ProblemProgress prog = progressRepo.findByUserAndProblem(user, p).orElseGet(() -> {
            ProblemProgress np = new ProblemProgress();
            np.setUser(user);
            np.setProblem(p);
            return np;
        });
        prog.setPreviousCode(prog.getLastCode());
        prog.setLastCode(code);
        prog.setLastLanguage(language);
        if (accepted) {
            prog.setAccepted(true);
            prog.setConsecutiveFailedSubmits(0);
        } else {
            prog.setConsecutiveFailedSubmits(prog.getConsecutiveFailedSubmits() + 1);
        }
        progressRepo.save(prog);
        stats.onSubmitResult(user, p.getTopicTags(), accepted, result.getOverallVerdict());
        Map<String, Object> dto = suiteDto(result, true);
        dto.put("accepted", accepted);
        dto.put("consecutiveFailedSubmits", prog.getConsecutiveFailedSubmits());
        dto.put("hintAvailable", prog.getConsecutiveFailedSubmits() >= props.getHints().getFailThreshold()
                && prog.getHintsUnlocked() < 3);
        return dto;
    }

    @Transactional
    public Map<String, Object> requestHint(UserAccount user, Long problemId, GenerationProgress progress,
                                           Consumer<String> onToken) {
        Problem p = requireOwned(problemId, user);
        ProblemProgress prog = progressRepo.findByUserAndProblem(user, p)
                .orElseThrow(() -> new IllegalStateException("Submit at least once before asking for a hint."));
        if (prog.getConsecutiveFailedSubmits() < props.getHints().getFailThreshold()) {
            throw new IllegalStateException("Hints unlock after " + props.getHints().getFailThreshold()
                    + " consecutive failed submits. Failed so far: " + prog.getConsecutiveFailedSubmits() + ".");
        }
        if (prog.getHintsUnlocked() >= 3) {
            throw new IllegalStateException("All hint levels are already unlocked.");
        }
        int nextLevel = prog.getHintsUnlocked() + 1;
        String lastVerdict = attempts.findByUserAndProblemOrderByCreatedAtDesc(user, p).stream()
                .filter(Attempt::isSubmit)
                .findFirst()
                .map(Attempt::getVerdict)
                .orElse("WA");
        progress.stage("check", "Hint " + nextLevel + " unlocked - reading your last attempt");
        progress.stage("prompt", "Asking " + props.getOllama().getModel() + " for hint level " + nextLevel);
        StringBuilder streamed = new StringBuilder();
        aiLock.callExclusive(() -> {
            ollama.streamText(hintPrompt(p, prog, nextLevel, lastVerdict), token -> {
                streamed.append(token);
                onToken.accept(token);
            });
            return null;
        });
        progress.stage("save", "Saving hint " + nextLevel);
        Hint h = new Hint();
        h.setUser(user);
        h.setProblem(p);
        h.setLevel(nextLevel);
        h.setText(streamed.toString().strip());
        hints.save(h);
        prog.setHintsUnlocked(nextLevel);
        progressRepo.save(prog);
        stats.onHintUsed(user);
        return Map.of("level", nextLevel, "text", h.getText());
    }

    @Transactional
    public void retry(UserAccount user, Long problemId) {
        Problem p = requireOwned(problemId, user);
        ProblemProgress prog = progressRepo.findByUserAndProblem(user, p).orElseGet(() -> {
            ProblemProgress np = new ProblemProgress();
            np.setUser(user);
            np.setProblem(p);
            return np;
        });
        prog.setConsecutiveFailedSubmits(0);
        prog.setHintsUnlocked(0);
        progressRepo.save(prog);
    }

    @Transactional
    public Map<String, Object> reportBadTests(UserAccount user, Long problemId) {
        Problem p = requireOwned(problemId, user);
        List<TestCase> all = testCases.findByProblemOrderBySortOrderAsc(p);
        SuiteResult check;
        if (p.getReferencePython() != null && !p.getReferencePython().isBlank()) {
            check = judge.runSuite("python", p.getReferencePython(), all, true);
            if (!check.allPassed() && p.getReferenceJava() != null && !p.getReferenceJava().isBlank()) {
                check = judge.runSuite("java", p.getReferenceJava(), all, true);
            }
        } else {
            check = judge.runSuite("java", p.getReferenceJava(), all, true);
        }
        if (check.allPassed()) {
            p.setVerified(true);
            problems.save(p);
            return Map.of("verified", true, "message", "Reference still matches all tests. The tests look consistent.");
        }
        p.setVerified(false);
        problems.save(p);
        return Map.of("verified", false, "message", "Reference no longer matches tests. Problem marked unverified. "
                + check.getSummary());
    }

    @Transactional
    public String postAcceptReview(UserAccount user, Long problemId, GenerationProgress progress,
                                   Consumer<String> onToken) {
        Problem p = requireOwned(problemId, user);
        ProblemProgress prog = progressRepo.findByUserAndProblem(user, p)
                .orElseThrow(() -> new IllegalStateException("Solve the problem first."));
        if (!prog.isAccepted()) {
            throw new IllegalStateException("Review is available only after an accepted submit.");
        }
        if (p.getPostAcceptReview() != null && !p.getPostAcceptReview().isBlank()) {
            progress.stage("cached", "Review already generated for this problem");
            return p.getPostAcceptReview();
        }
        progress.stage("check", "Confirming your accepted submission");
        progress.stage("prompt", "Asking " + props.getOllama().getModel() + " for a complexity review");
        String prompt = """
                The learner already solved this problem. Give a short post-accept review: time/space complexity
                of a typical solution and one alternative approach. Do not paste a full solution.
                Title: %s
                Statement: %s
                """.formatted(p.getTitle(), p.getStatement());
        StringBuilder streamed = new StringBuilder();
        aiLock.callExclusive(() -> {
            ollama.streamText(prompt, token -> {
                streamed.append(token);
                onToken.accept(token);
            });
            return null;
        });
        progress.stage("save", "Caching the review on this problem");
        p.setPostAcceptReview(streamed.toString().strip());
        problems.save(p);
        return p.getPostAcceptReview();
    }

    @Transactional
    public void saveNote(UserAccount user, Long problemId, String content) {
        Problem p = requireOwned(problemId, user);
        Note n = notes.findByUserAndProblem(user, p).orElseGet(() -> {
            Note nn = new Note();
            nn.setUser(user);
            nn.setProblem(p);
            return nn;
        });
        n.setContent(content == null ? "" : content);
        notes.save(n);
    }

    private void saveAttempt(UserAccount user, Problem p, String language, String code,
                             SuiteResult result, boolean submit, boolean accepted) {
        Attempt a = new Attempt();
        a.setUser(user);
        a.setProblem(p);
        a.setLanguage(language);
        a.setCode(code);
        a.setVerdict(result.getOverallVerdict());
        a.setSubmit(submit);
        a.setAccepted(accepted);
        a.setPassed(result.getPassed());
        a.setTotal(result.getTotal());
        a.setMessage(result.getSummary());
        attempts.save(a);
    }

    private Map<String, Object> suiteDto(SuiteResult result, boolean submit) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("verdict", result.getOverallVerdict());
        m.put("passed", result.getPassed());
        m.put("total", result.getTotal());
        m.put("summary", result.getSummary());
        m.put("cases", result.getCases().stream().map(c -> caseDto(c, submit)).toList());
        return m;
    }

    private Map<String, Object> caseDto(CaseResult c, boolean submit) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("verdict", c.getVerdict());
        m.put("hidden", c.isHidden());
        if (!c.isHidden()) {
            m.put("input", c.getInput());
            m.put("expected", c.getExpected());
            m.put("stdout", c.getStdout());
            m.put("stderr", c.getStderr());
        } else if (submit) {
            m.put("category", hiddenCategory(c.getVerdict()));
        }
        return m;
    }

    private String hiddenCategory(String verdict) {
        return switch (verdict) {
            case "AC" -> "passed";
            case "TLE" -> "time limit on a hidden case";
            case "WA" -> "wrong answer on an edge or large case";
            case "RE" -> "runtime error on a hidden case";
            default -> verdict;
        };
    }

    private String hintPrompt(Problem p, ProblemProgress prog, int level, String lastVerdict) {
        String policy = switch (level) {
            case 1 -> "Level 1 NUDGE only: ask a question about an edge case. Do not name the algorithm. Do not give code.";
            case 2 -> "Level 2 APPROACH: name the technique (e.g. two pointers) and a one-sentence plan. No full code.";
            default -> "Level 3 EDGE CASE: point out a likely missed case matching the last verdict. Still no complete solution listing.";
        };
        return """
                You write on-site hints for a coding practice site. Never dump a full solution.
                %s
                Last verdict: %s
                Problem title: %s
                Statement: %s
                Learner last code:
                %s
                """.formatted(policy, lastVerdict, p.getTitle(), p.getStatement(),
                prog.getLastCode() == null ? "" : prog.getLastCode());
    }

    private String normalizeLang(String language) {
        if (language == null) {
            return "python";
        }
        String l = language.toLowerCase();
        if (l.startsWith("java")) {
            return "java";
        }
        if (l.startsWith("py")) {
            return "python";
        }
        throw new IllegalArgumentException("Only Java and Python are supported.");
    }
}
