package com.practice.platform.judge;

import java.util.ArrayList;
import java.util.List;

public class SuiteResult {
    private String overallVerdict = "AC";
    private int passed;
    private int total;
    private String summary = "";
    private final List<CaseResult> cases = new ArrayList<>();

    public String getOverallVerdict() { return overallVerdict; }
    public void setOverallVerdict(String overallVerdict) { this.overallVerdict = overallVerdict; }
    public int getPassed() { return passed; }
    public void setPassed(int passed) { this.passed = passed; }
    public int getTotal() { return total; }
    public void setTotal(int total) { this.total = total; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public List<CaseResult> getCases() { return cases; }

    public boolean allPassed() {
        return passed == total && total > 0 && "AC".equals(overallVerdict);
    }
}
