package com.practice.platform.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.practice.platform.ai.GeneratedProblemPayload;
import com.practice.platform.ai.OllamaClient;
import com.practice.platform.config.AppProperties;
import com.practice.platform.entity.Problem;
import com.practice.platform.entity.TestCase;
import com.practice.platform.entity.UserAccount;
import com.practice.platform.judge.JudgeService;
import com.practice.platform.judge.SuiteResult;
import com.practice.platform.repo.ProblemRepository;
import com.practice.platform.repo.TestCaseRepository;
import com.practice.platform.repo.UserAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class GenerationService {

    private final OllamaClient ollama;
    private final ObjectMapper mapper;
    private final JudgeService judge;
    private final ProblemRepository problems;
    private final TestCaseRepository tests;
    private final LocalAiLock aiLock;
    private final AppProperties props;
    private final UserAccountRepository users;
    private final TransactionTemplate tx;

    public GenerationService(OllamaClient ollama, ObjectMapper mapper, JudgeService judge,
                             ProblemRepository problems, TestCaseRepository tests,
                             UserAccountRepository users,
                             LocalAiLock aiLock, AppProperties props, TransactionTemplate tx) {
        this.ollama = ollama;
        this.mapper = mapper;
        this.judge = judge;
        this.problems = problems;
        this.tests = tests;
        this.users = users;
        this.aiLock = aiLock;
        this.props = props;
        this.tx = tx;
    }

    public Problem generate(UserAccount user, String prompt, String difficulty, String topic) {
        return generate(user, prompt, difficulty, topic, GenerationProgress.NONE);
    }

    public Problem generate(UserAccount user, String prompt, String difficulty, String topic,
                            GenerationProgress progress) {
        return aiLock.callExclusive(() -> {
            try {
                return createValidated(user, prompt, difficulty, topic, true, progress);
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new IllegalStateException(e.getMessage(), e);
            }
        });
    }

    private Problem createValidated(UserAccount user, String prompt, String difficulty, String topic,
                                    boolean allowRetry, GenerationProgress progress) throws Exception {
        if (!allowRetry) {
            progress.stage("retry", "First attempt was rejected - regenerating with a stricter prompt");
        }
        progress.stage("prompt", "Prompting " + props.getOllama().getModel() + " on this machine");
        List<String> recent = recentTitles(user);
        String json = ollama.generateJson(buildPrompt(prompt, difficulty, topic, user.getSuggestedTopic(), recent));
        progress.stage("parse", "Parsing the generated JSON problem");
        GeneratedProblemPayload payload = parse(json);
        List<TestCase> draft = toDraftCases(payload);
        progress.stage("tests", "Checking test structure and looking for duplicates");
        try {
            validateShape(payload, draft);
        } catch (IllegalStateException shape) {
            if (allowRetry) {
                return createValidated(user, prompt + "\nFix JSON shape: 2 public + 3 hidden tests, both references.",
                        difficulty, topic, false, progress);
            }
            throw shape;
        }

        if (allowRetry && isDuplicate(user, payload.title)) {
            return createValidated(user,
                    prompt + "\nDo not reuse the problem titled \"" + payload.title.strip()
                            + "\". Produce a clearly different problem.",
                    difficulty, topic, false, progress);
        }

        progress.stage("selfcheck", "Running the reference solution against every generated test");
        SuiteResult check = runReference(payload, draft);
        if (!check.allPassed()) {
            if (allowRetry) {
                return createValidated(user,
                        prompt + "\nSelf-check failed. Make expected outputs match the reference stdin/stdout programs.",
                        difficulty, topic, false, progress);
            }
            throw new IllegalStateException("Generated problem failed self-check (" + check.getOverallVerdict()
                    + "). Rephrase your request. " + check.getSummary());
        }

        progress.stage("save", "Saving the verified problem to your library");
        return tx.execute(status -> save(user, payload, draft, prompt, difficulty, topic));
    }

    private Problem save(UserAccount user, GeneratedProblemPayload payload, List<TestCase> draft,
                         String prompt, String difficulty, String topic) {
        Problem p = new Problem();
        p.setOwner(users.getReferenceById(user.getId()));
        p.setTitle(payload.title.strip());
        p.setStatement(payload.statement.strip());
        p.setConstraintsText(payload.constraintsText());
        p.setDifficulty(normalizeDifficulty(payload.difficulty, difficulty));
        p.setTopicTags(payload.topicTags == null || payload.topicTags.isEmpty()
                ? (topic == null ? "" : topic)
                : String.join(", ", payload.topicTags));
        p.setStarterJava(payload.starterJava);
        p.setStarterPython(payload.starterPython);
        p.setReferenceJava(payload.referenceJava);
        p.setReferencePython(payload.referencePython);
        p.setOriginalPrompt(prompt);
        p.setVerified(true);
        problems.save(p);
        int i = 0;
        for (TestCase tc : draft) {
            tc.setProblem(p);
            tc.setSortOrder(i++);
            tests.save(tc);
        }
        return p;
    }

    private SuiteResult runReference(GeneratedProblemPayload payload, List<TestCase> draft) {
        if (payload.referencePython != null && !payload.referencePython.isBlank()) {
            SuiteResult py = judge.runSuite("python", payload.referencePython, draft, true);
            if (py.allPassed()) {
                return py;
            }
            if (payload.referenceJava == null || payload.referenceJava.isBlank()) {
                return py;
            }
        }
        if (payload.referenceJava != null && !payload.referenceJava.isBlank()) {
            return judge.runSuite("java", payload.referenceJava, draft, true);
        }
        SuiteResult empty = new SuiteResult();
        empty.setOverallVerdict("RE");
        empty.setSummary("Missing reference solution.");
        return empty;
    }

    private List<TestCase> toDraftCases(GeneratedProblemPayload payload) {
        List<TestCase> list = new ArrayList<>();
        int i = 0;
        if (payload.tests == null) {
            return list;
        }
        for (GeneratedProblemPayload.GeneratedTest t : payload.tests) {
            TestCase tc = new TestCase();
            tc.setInputData(t.inputText());
            tc.setExpectedOutput(t.expectedText());
            tc.setHidden(t.hidden);
            tc.setSortOrder(i++);
            list.add(tc);
        }
        return list;
    }

    private void validateShape(GeneratedProblemPayload payload, List<TestCase> draft) {
        if (payload.title == null || payload.title.isBlank() || payload.statement == null || payload.statement.isBlank()) {
            throw new IllegalStateException("Model returned an incomplete problem. Try again.");
        }
        long pub = draft.stream().filter(t -> !t.isHidden()).count();
        long hid = draft.stream().filter(TestCase::isHidden).count();
        if (draft.size() < 4 || pub < 2 || hid < 2) {
            throw new IllegalStateException("Need at least 2 public and 2 hidden tests. Try generating again.");
        }
        if ((payload.referencePython == null || payload.referencePython.isBlank())
                && (payload.referenceJava == null || payload.referenceJava.isBlank())) {
            throw new IllegalStateException("Model omitted a reference solution.");
        }
    }

    private GeneratedProblemPayload parse(String json) throws Exception {
        String cleaned = json.strip();
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.replaceAll("^```(?:json)?", "").replaceAll("```$", "").strip();
        }
        return mapper.readValue(cleaned, GeneratedProblemPayload.class);
    }

    private String normalizeDifficulty(String fromModel, String requested) {
        String d = fromModel == null ? requested : fromModel;
        if (d == null) {
            return "MEDIUM";
        }
        d = d.trim().toUpperCase(Locale.ROOT);
        if (d.contains("EASY")) return "EASY";
        if (d.contains("HARD")) return "HARD";
        return "MEDIUM";
    }

    private List<String> recentTitles(UserAccount user) {
        return problems.findTitlesByOwner(user).stream().limit(10).toList();
    }

    private boolean isDuplicate(UserAccount user, String title) {
        if (title == null || title.isBlank()) {
            return false;
        }
        String normalized = normalizeTitle(title);
        return problems.findTitlesByOwner(user).stream()
                .map(this::normalizeTitle)
                .anyMatch(normalized::equals);
    }

    private String normalizeTitle(String title) {
        return title.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").strip();
    }

    private String buildPrompt(String userPrompt, String difficulty, String topic, String weakTopic,
                               List<String> recentTitles) {
        String weak = (weakTopic == null || weakTopic.isBlank())
                ? ""
                : " The learner has been struggling with: " + weakTopic + ". You may use that topic if the request is vague.";
        String avoid = recentTitles == null || recentTitles.isEmpty()
                ? ""
                : " Do NOT repeat or closely paraphrase any of these problems the learner already has: "
                        + String.join(" | ", recentTitles) + ".";
        return """
                You are a strict coding-interview problem author. Reply with a single JSON object only.
                Languages allowed for starter/reference: Java and Python only. Programs MUST read from stdin and write to stdout.
                Java reference must be a complete file with `public class Main` and `public static void main`.
                Python reference must be a complete script.
                Include at least 2 public tests (hidden=false) and at least 3 hidden tests (hidden=true).
                Hidden tests should cover edge cases (empty, single element, duplicates) without huge input.
                Fields:
                title, statement, constraints, difficulty (EASY|MEDIUM|HARD), topicTags (array of strings),
                starterJava, starterPython, referenceJava, referencePython,
                tests: array of {input, expected, hidden}.
                User difficulty preference: %s
                Topic hint: %s
                %s
                %s
                User request:
                %s
                """.formatted(
                difficulty == null ? "MEDIUM" : difficulty,
                topic == null ? "" : topic,
                weak,
                avoid,
                userPrompt
        );
    }

    public String getModelName() {
        return props.getOllama().getModel();
    }
}
