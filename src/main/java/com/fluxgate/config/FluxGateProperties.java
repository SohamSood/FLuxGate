package com.fluxgate.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration properties for FluxGate reverse proxy and load balancer.
 */
@ConfigurationProperties(prefix = "fluxgate")
public class FluxGateProperties {

    /**
     * List of backend URLs (e.g. "http://localhost:8081", "http://localhost:8082").
     */
    private List<String> backends = new ArrayList<>(List.of(
            "http://localhost:8081",
            "http://localhost:8082",
            "http://localhost:8083"
    ));

    private HealthCheck healthCheck = new HealthCheck();
    private Proxy proxy = new Proxy();
    private DemoBackends demoBackends = new DemoBackends();
    private RateLimit rateLimit = new RateLimit();

    public List<String> getBackends() {
        return backends;
    }

    public void setBackends(List<String> backends) {
        this.backends = backends;
    }

    public HealthCheck getHealthCheck() {
        return healthCheck;
    }

    public void setHealthCheck(HealthCheck healthCheck) {
        this.healthCheck = healthCheck;
    }

    public Proxy getProxy() {
        return proxy;
    }

    public void setProxy(Proxy proxy) {
        this.proxy = proxy;
    }

    public DemoBackends getDemoBackends() {
        return demoBackends;
    }

    public void setDemoBackends(DemoBackends demoBackends) {
        this.demoBackends = demoBackends;
    }

    public RateLimit getRateLimit() {
        return rateLimit;
    }

    public void setRateLimit(RateLimit rateLimit) {
        this.rateLimit = rateLimit;
    }

    public static class DemoBackends {
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class HealthCheck {
        private boolean enabled = true;
        private long intervalMs = 10000;
        private int timeoutMs = 2000;
        private String healthPath = "/health";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public long getIntervalMs() {
            return intervalMs;
        }

        public void setIntervalMs(long intervalMs) {
            this.intervalMs = intervalMs;
        }

        public int getTimeoutMs() {
            return timeoutMs;
        }

        public void setTimeoutMs(int timeoutMs) {
            this.timeoutMs = timeoutMs;
        }

        public String getHealthPath() {
            return healthPath;
        }

        public void setHealthPath(String healthPath) {
            this.healthPath = healthPath;
        }
    }

    public static class Proxy {
        private int connectTimeoutMs = 3000;
        private int readTimeoutMs = 10000;

        public int getConnectTimeoutMs() {
            return connectTimeoutMs;
        }

        public void setConnectTimeoutMs(int connectTimeoutMs) {
            this.connectTimeoutMs = connectTimeoutMs;
        }

        public int getReadTimeoutMs() {
            return readTimeoutMs;
        }

        public void setReadTimeoutMs(int readTimeoutMs) {
            this.readTimeoutMs = readTimeoutMs;
        }
    }

    public static class RateLimit {
        private boolean enabled = true;
        private int capacity = 60;
        private int refillRatePerMinute = 60;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getCapacity() {
            return capacity;
        }

        public void setCapacity(int capacity) {
            this.capacity = capacity;
        }

        public int getRefillRatePerMinute() {
            return refillRatePerMinute;
        }

        public void setRefillRatePerMinute(int refillRatePerMinute) {
            this.refillRatePerMinute = refillRatePerMinute;
        }
    }
}
