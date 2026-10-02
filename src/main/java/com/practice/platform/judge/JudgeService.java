package com.practice.platform.judge;

import com.practice.platform.config.AppProperties;
import com.practice.platform.entity.TestCase;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

@Service
public class JudgeService {

    private final AppProperties props;
    private volatile Boolean dockerAvailable;

    public JudgeService(AppProperties props) {
        this.props = props;
    }

    public SuiteResult runSuite(String language, String code, List<TestCase> tests, boolean includeHidden) {
        List<TestCase> selected = tests.stream()
                .filter(t -> includeHidden || !t.isHidden())
                .toList();
        SuiteResult suite = new SuiteResult();
        suite.setTotal(selected.size());
        if (selected.isEmpty()) {
            suite.setOverallVerdict("RE");
            suite.setSummary("No test cases to run.");
            return suite;
        }

        Path work = null;
        try {
            work = Files.createTempDirectory("judge-");
            writeSource(work, language, code);
            String compileErr = compileIfNeeded(work, language);
            if (compileErr != null) {
                suite.setOverallVerdict("CE");
                suite.setSummary(compileErr);
                suite.getCases().add(new CaseResult("CE", "", compileErr, false, "", ""));
                return suite;
            }

            String worst = "AC";
            int passed = 0;
            for (TestCase tc : selected) {
                CaseResult cr = runOne(work, language, tc);
                suite.getCases().add(cr);
                if ("AC".equals(cr.getVerdict())) {
                    passed++;
                } else if ("AC".equals(worst)) {
                    worst = cr.getVerdict();
                }
            }
            suite.setPassed(passed);
            suite.setOverallVerdict(passed == selected.size() ? "AC" : worst);
            suite.setSummary(buildSummary(suite, includeHidden));
            return suite;
        } catch (Exception e) {
            suite.setOverallVerdict("RE");
            suite.setSummary("Judge error: " + e.getMessage());
            return suite;
        } finally {
            if (work != null) {
                deleteRecursive(work);
            }
        }
    }

    private String buildSummary(SuiteResult suite, boolean includeHidden) {
        if ("AC".equals(suite.getOverallVerdict())) {
            return suite.getPassed() + "/" + suite.getTotal() + " passed.";
        }
        StringBuilder sb = new StringBuilder();
        sb.append(suite.getPassed()).append("/").append(suite.getTotal()).append(" passed. Verdict: ")
                .append(suite.getOverallVerdict()).append(". ");
        for (CaseResult c : suite.getCases()) {
            if (!"AC".equals(c.getVerdict())) {
                if (c.isHidden() && includeHidden) {
                    sb.append("A hidden test failed (").append(category(c)).append(").");
                } else if (!c.isHidden()) {
                    sb.append("Public case failed. Input:\n").append(c.getInput())
                            .append("\nExpected:\n").append(c.getExpected())
                            .append("\nGot:\n").append(c.getStdout());
                    if (c.getStderr() != null && !c.getStderr().isBlank()) {
                        sb.append("\nStderr:\n").append(c.getStderr());
                    }
                }
                break;
            }
        }
        return sb.toString();
    }

    private String category(CaseResult c) {
        return switch (c.getVerdict()) {
            case "TLE" -> "time limit";
            case "WA" -> "wrong answer on an edge or large case";
            case "RE" -> "runtime error";
            default -> c.getVerdict();
        };
    }

    private void writeSource(Path work, String language, String code) throws IOException {
        if ("java".equalsIgnoreCase(language)) {
            Files.writeString(work.resolve("Main.java"), ensureJavaMain(code), StandardCharsets.UTF_8);
        } else {
            Files.writeString(work.resolve("solution.py"), code, StandardCharsets.UTF_8);
        }
    }

    private String ensureJavaMain(String code) {
        if (code.contains("class Main")) {
            return code;
        }
        return """
                import java.util.*;
                import java.io.*;
                public class Main {
                %s
                }
                """.formatted(code);
    }

    private String compileIfNeeded(Path work, String language) throws IOException, InterruptedException {
        if (!"java".equalsIgnoreCase(language)) {
            return null;
        }
        ExecResult r = exec(work, List.of("javac", "Main.java"), "", props.getJudge().getTimeoutSeconds() + 10, true);
        if (r.timedOut) {
            return "Compilation timed out.";
        }
        if (r.exitCode != 0) {
            return (r.stderr.isBlank() ? r.stdout : r.stderr).strip();
        }
        return null;
    }

    private CaseResult runOne(Path work, String language, TestCase tc) throws IOException, InterruptedException {
        List<String> cmd;
        if ("java".equalsIgnoreCase(language)) {
            cmd = List.of("java", "-Xmx" + props.getJudge().getMemoryMb() + "m", "Main");
        } else {
            cmd = pythonCmd(work);
        }
        ExecResult r = exec(work, cmd, tc.getInputData() == null ? "" : tc.getInputData(),
                props.getJudge().getTimeoutSeconds(), false);
        if (r.timedOut) {
            return new CaseResult("TLE", r.stdout, r.stderr, tc.isHidden(), tc.getInputData(), tc.getExpectedOutput());
        }
        if (r.exitCode != 0) {
            return new CaseResult("RE", r.stdout, r.stderr, tc.isHidden(), tc.getInputData(), tc.getExpectedOutput());
        }
        if (normalize(r.stdout).equals(normalize(tc.getExpectedOutput()))) {
            return new CaseResult("AC", r.stdout, r.stderr, tc.isHidden(), tc.getInputData(), tc.getExpectedOutput());
        }
        return new CaseResult("WA", r.stdout, r.stderr, tc.isHidden(), tc.getInputData(), tc.getExpectedOutput());
    }

    private List<String> pythonCmd(Path work) {
        if (useDocker()) {
            return List.of("python", "solution.py");
        }
        return List.of(findPython(), "solution.py");
    }

    private String findPython() {
        if (isWindows()) {
            return "python";
        }
        return "python3";
    }

    private boolean useDocker() {
        if (!props.getJudge().isPreferDocker()) {
            return false;
        }
        if (dockerAvailable != null) {
            return dockerAvailable;
        }
        dockerAvailable = probeDocker();
        return dockerAvailable;
    }

    private boolean probeDocker() {
        try {
            Process p = new ProcessBuilder("docker", "version", "--format", "{{.Server.Version}}")
                    .redirectErrorStream(true)
                    .start();
            boolean ok = p.waitFor(8, TimeUnit.SECONDS) && p.exitValue() == 0;
            if (!ok) {
                p.destroyForcibly();
            }
            return ok;
        } catch (Exception e) {
            return false;
        }
    }

    private ExecResult exec(Path work, List<String> command, String stdin, int timeoutSec, boolean compile)
            throws IOException, InterruptedException {
        if (useDocker() && !compileUsesHostJavac(command)) {
            return execDocker(work, command, stdin, timeoutSec);
        }
        if (useDocker() && compile && "javac".equals(command.get(0))) {
            return execDocker(work, command, stdin, timeoutSec);
        }
        return execLocal(work, command, stdin, timeoutSec);
    }

    private boolean compileUsesHostJavac(List<String> command) {
        return false;
    }

    private ExecResult execDocker(Path work, List<String> inner, String stdin, int timeoutSec)
            throws IOException, InterruptedException {
        String image = inner.get(0).equals("python") || inner.get(0).equals("python3")
                ? "python:3.12-slim"
                : "eclipse-temurin:17-jdk";
        List<String> cmd = new java.util.ArrayList<>();
        cmd.add("docker");
        cmd.add("run");
        cmd.add("--rm");
        cmd.add("-i");
        cmd.add("--network");
        cmd.add("none");
        cmd.add("--memory");
        cmd.add(props.getJudge().getMemoryMb() + "m");
        cmd.add("--cpus");
        cmd.add("0.5");
        cmd.add("-v");
        cmd.add(work.toAbsolutePath() + ":/workspace");
        cmd.add("-w");
        cmd.add("/workspace");
        cmd.add(image);
        cmd.addAll(inner);
        return runProcess(cmd, work, stdin, timeoutSec + 15);
    }

    private ExecResult execLocal(Path work, List<String> command, String stdin, int timeoutSec)
            throws IOException, InterruptedException {
        return runProcess(command, work, stdin, timeoutSec);
    }

    private ExecResult runProcess(List<String> command, Path work, String stdin, int timeoutSec)
            throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(work.toFile());
        pb.redirectErrorStream(false);
        Process p = pb.start();
        if (stdin != null) {
            p.getOutputStream().write(stdin.getBytes(StandardCharsets.UTF_8));
        }
        p.getOutputStream().close();
        StreamGobbler out = new StreamGobbler(p.getInputStream());
        StreamGobbler err = new StreamGobbler(p.getErrorStream());
        out.start();
        err.start();
        boolean finished = p.waitFor(timeoutSec, TimeUnit.SECONDS);
        if (!finished) {
            p.destroyForcibly();
            out.join(500);
            err.join(500);
            return new ExecResult(-1, out.text(), err.text(), true);
        }
        out.join(1000);
        err.join(1000);
        return new ExecResult(p.exitValue(), out.text(), err.text(), false);
    }

    private static String normalize(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\r\n", "\n").strip();
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    private static void deleteRecursive(Path root) {
        try (Stream<Path> walk = Files.walk(root)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                }
            });
        } catch (IOException ignored) {
        }
    }

    private static final class StreamGobbler extends Thread {
        private final InputStream in;
        private final StringBuilder sb = new StringBuilder();

        StreamGobbler(InputStream in) {
            this.in = in;
        }

        @Override
        public void run() {
            try {
                sb.append(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            } catch (IOException ignored) {
            }
        }

        String text() {
            return sb.toString();
        }
    }

    private record ExecResult(int exitCode, String stdout, String stderr, boolean timedOut) {
    }
}
