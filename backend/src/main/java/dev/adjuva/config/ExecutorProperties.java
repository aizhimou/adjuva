package dev.adjuva.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "adjuva")
public class ExecutorProperties {
    private String apiBaseUrl = "http://localhost:8080";
    private final Codex codex = new Codex();
    private final Mock mock = new Mock();

    public String getApiBaseUrl() { return apiBaseUrl; }
    public void setApiBaseUrl(String apiBaseUrl) { this.apiBaseUrl = apiBaseUrl; }
    public Codex getCodex() { return codex; }
    public Mock getMock() { return mock; }

    public static class Codex {
        private String bin = "codex";
        public String getBin() { return bin; }
        public void setBin(String bin) { this.bin = bin; }
    }

    public static class Mock {
        private long waitTimeoutSeconds = 300;
        public long getWaitTimeoutSeconds() { return waitTimeoutSeconds; }
        public void setWaitTimeoutSeconds(long waitTimeoutSeconds) { this.waitTimeoutSeconds = waitTimeoutSeconds; }
    }
}
