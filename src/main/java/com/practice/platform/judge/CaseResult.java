package com.practice.platform.judge;

public class CaseResult {
    private final String verdict;
    private final String stdout;
    private final String stderr;
    private final boolean hidden;
    private final String input;
    private final String expected;

    public CaseResult(String verdict, String stdout, String stderr, boolean hidden, String input, String expected) {
        this.verdict = verdict;
        this.stdout = stdout;
        this.stderr = stderr;
        this.hidden = hidden;
        this.input = input;
        this.expected = expected;
    }

    public String getVerdict() { return verdict; }
    public String getStdout() { return stdout; }
    public String getStderr() { return stderr; }
    public boolean isHidden() { return hidden; }
    public String getInput() { return input; }
    public String getExpected() { return expected; }
}
