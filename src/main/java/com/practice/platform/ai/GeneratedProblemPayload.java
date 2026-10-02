package com.practice.platform.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@JsonIgnoreProperties(ignoreUnknown = true)
public class GeneratedProblemPayload {
    public String title;
    public String statement;
    public Object constraints;
    public String difficulty;
    public List<String> topicTags = new ArrayList<>();
    public String starterJava;
    public String starterPython;
    public String referenceJava;
    public String referencePython;
    public List<GeneratedTest> tests = new ArrayList<>();

    public String constraintsText() {
        if (constraints == null) {
            return "";
        }
        if (constraints instanceof String s) {
            return s.strip();
        }
        if (constraints instanceof List<?> list) {
            return list.stream()
                    .map(String::valueOf)
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.joining("; "));
        }
        if (constraints instanceof Map<?, ?> map) {
            return map.entrySet().stream()
                    .map(e -> e.getKey() + ": " + e.getValue())
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.joining("; "));
        }
        return String.valueOf(constraints).trim();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GeneratedTest {
        public Object input;
        public Object expected;
        @JsonProperty("hidden")
        public boolean hidden;

        public String inputText() {
            return normalizeScalar(input);
        }

        public String expectedText() {
            return normalizeScalar(expected);
        }

        private String normalizeScalar(Object value) {
            if (value == null) {
                return "";
            }
            if (value instanceof String s) {
                return s.trim();
            }
            if (value instanceof Number || value instanceof Boolean) {
                return String.valueOf(value);
            }
            return String.valueOf(value).trim();
        }
    }
}
