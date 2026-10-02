package com.practice.platform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app")
public class AppProperties {
    private String jwtSecret;
    private long jwtExpirationMs;
    private final Ollama ollama = new Ollama();
    private final Judge judge = new Judge();
    private final Hints hints = new Hints();

    public String getJwtSecret() { return jwtSecret; }
    public void setJwtSecret(String jwtSecret) { this.jwtSecret = jwtSecret; }
    public long getJwtExpirationMs() { return jwtExpirationMs; }
    public void setJwtExpirationMs(long jwtExpirationMs) { this.jwtExpirationMs = jwtExpirationMs; }
    public Ollama getOllama() { return ollama; }
    public Judge getJudge() { return judge; }
    public Hints getHints() { return hints; }

    public static class Ollama {
        private String baseUrl;
        private String model;
        private int timeoutSeconds = 180;
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
        public int getTimeoutSeconds() { return timeoutSeconds; }
        public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
    }

    public static class Judge {
        private int timeoutSeconds = 5;
        private int memoryMb = 256;
        private boolean preferDocker = true;
        public int getTimeoutSeconds() { return timeoutSeconds; }
        public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
        public int getMemoryMb() { return memoryMb; }
        public void setMemoryMb(int memoryMb) { this.memoryMb = memoryMb; }
        public boolean isPreferDocker() { return preferDocker; }
        public void setPreferDocker(boolean preferDocker) { this.preferDocker = preferDocker; }
    }

    public static class Hints {
        private int failThreshold = 3;
        public int getFailThreshold() { return failThreshold; }
        public void setFailThreshold(int failThreshold) { this.failThreshold = failThreshold; }
    }
}
