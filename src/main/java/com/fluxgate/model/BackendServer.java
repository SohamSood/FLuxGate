package com.fluxgate.model;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Represents a registered backend server instance in FluxGate.
 */
public class BackendServer {

    private final String url;
    private final AtomicBoolean healthy;
    private final AtomicInteger consecutiveFailures;
    private final AtomicInteger consecutiveSuccesses;
    private final AtomicLong totalRequests;
    private final AtomicLong activeRequests;
    private final AtomicInteger weight;
    private volatile long lastCheckedAt;
    private volatile String lastFailureReason;

    public BackendServer(String url) {
        this(url, true, 1);
    }

    public BackendServer(String url, boolean initialHealthy) {
        this(url, initialHealthy, 1);
    }

    public BackendServer(String url, boolean initialHealthy, int weight) {
        // Strip trailing slash for consistent URI joining
        this.url = sanitizeUrl(url);
        this.healthy = new AtomicBoolean(initialHealthy);
        this.consecutiveFailures = new AtomicInteger(0);
        this.consecutiveSuccesses = new AtomicInteger(0);
        this.totalRequests = new AtomicLong(0);
        this.activeRequests = new AtomicLong(0);
        this.weight = new AtomicInteger(Math.max(1, weight));
        this.lastCheckedAt = System.currentTimeMillis();
    }

    public static String sanitizeUrl(String raw) {
        Objects.requireNonNull(raw, "Backend URL must not be null");
        String trimmed = raw.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    public String getUrl() {
        return url;
    }

    public boolean isHealthy() {
        return healthy.get();
    }

    public void markHealthy() {
        consecutiveFailures.set(0);
        consecutiveSuccesses.incrementAndGet();
        lastCheckedAt = System.currentTimeMillis();
        lastFailureReason = null;
        healthy.set(true);
    }

    public void markUnhealthy(String reason) {
        consecutiveSuccesses.set(0);
        consecutiveFailures.incrementAndGet();
        lastCheckedAt = System.currentTimeMillis();
        lastFailureReason = reason;
        healthy.set(false);
    }

    public void incrementRequests() {
        totalRequests.incrementAndGet();
        activeRequests.incrementAndGet();
    }

    public void decrementActiveRequests() {
        activeRequests.decrementAndGet();
    }

    public long getTotalRequests() {
        return totalRequests.get();
    }

    public long getActiveRequests() {
        return Math.max(0, activeRequests.get());
    }

    public int getConsecutiveFailures() {
        return consecutiveFailures.get();
    }

    public int getConsecutiveSuccesses() {
        return consecutiveSuccesses.get();
    }

    public long getLastCheckedAt() {
        return lastCheckedAt;
    }

    public String getLastFailureReason() {
        return lastFailureReason;
    }

    public int getWeight() {
        return weight.get();
    }

    public void setWeight(int newWeight) {
        this.weight.set(Math.max(1, newWeight));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BackendServer that = (BackendServer) o;
        return Objects.equals(url, that.url);
    }

    @Override
    public int hashCode() {
        return Objects.hash(url);
    }

    @Override
    public String toString() {
        return "BackendServer{" +
                "url='" + url + '\'' +
                ", healthy=" + healthy.get() +
                ", weight=" + weight.get() +
                ", totalRequests=" + totalRequests.get() +
                '}';
    }
}
