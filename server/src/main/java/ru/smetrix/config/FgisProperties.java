package ru.smetrix.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "fgis")
public class FgisProperties {

    private boolean enabled;
    private String importKey;
    private final Api api = new Api();
    private final Scheduler scheduler = new Scheduler();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getImportKey() {
        return importKey;
    }

    public void setImportKey(String importKey) {
        this.importKey = importKey;
    }

    public Api getApi() {
        return api;
    }

    public Scheduler getScheduler() {
        return scheduler;
    }

    public static class Api {
        private String downloadUrlTemplate;
        private String sessionUrlTemplate;
        private String period;
        private List<String> regionCodes = new ArrayList<>();
        private int connectTimeoutSeconds = 20;
        private int readTimeoutSeconds = 120;
        private long maxDownloadBytes = 104_857_600L;
        private int maxAttempts = 3;
        private long retryDelayMs = 2_000L;

        public String getDownloadUrlTemplate() {
            return downloadUrlTemplate;
        }

        public void setDownloadUrlTemplate(String downloadUrlTemplate) {
            this.downloadUrlTemplate = downloadUrlTemplate;
        }

        public String getSessionUrlTemplate() { return sessionUrlTemplate; }
        public void setSessionUrlTemplate(String value) { this.sessionUrlTemplate = value; }

        public String getPeriod() {
            return period;
        }

        public void setPeriod(String period) {
            this.period = period;
        }

        public List<String> getRegionCodes() {
            return regionCodes;
        }

        public void setRegionCodes(List<String> regionCodes) {
            this.regionCodes = regionCodes == null ? new ArrayList<>() : new ArrayList<>(regionCodes);
        }

        public int getConnectTimeoutSeconds() {
            return connectTimeoutSeconds;
        }

        public void setConnectTimeoutSeconds(int connectTimeoutSeconds) {
            this.connectTimeoutSeconds = connectTimeoutSeconds;
        }

        public int getReadTimeoutSeconds() {
            return readTimeoutSeconds;
        }

        public void setReadTimeoutSeconds(int readTimeoutSeconds) {
            this.readTimeoutSeconds = readTimeoutSeconds;
        }

        public long getMaxDownloadBytes() {
            return maxDownloadBytes;
        }

        public void setMaxDownloadBytes(long maxDownloadBytes) {
            this.maxDownloadBytes = maxDownloadBytes;
        }

        public int getMaxAttempts() { return maxAttempts; }
        public void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }
        public long getRetryDelayMs() { return retryDelayMs; }
        public void setRetryDelayMs(long retryDelayMs) { this.retryDelayMs = retryDelayMs; }
    }

    public static class Scheduler {
        private String cron = "0 0 3 1 1,4,7,10 *";
        private long regionRefreshDelayMs = 300_000L;
        private int freshnessHours = 24;

        public String getCron() {
            return cron;
        }

        public void setCron(String cron) {
            this.cron = cron;
        }

        public long getRegionRefreshDelayMs() {
            return regionRefreshDelayMs;
        }

        public void setRegionRefreshDelayMs(long regionRefreshDelayMs) {
            this.regionRefreshDelayMs = regionRefreshDelayMs;
        }

        public int getFreshnessHours() {
            return freshnessHours;
        }

        public void setFreshnessHours(int freshnessHours) {
            this.freshnessHours = freshnessHours;
        }
    }
}
