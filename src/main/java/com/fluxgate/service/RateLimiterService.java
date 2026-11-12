package com.fluxgate.service;

import com.fluxgate.config.FluxGateProperties;
import com.fluxgate.model.RateLimitResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread-safe Token-Bucket Rate Limiter providing per-IP and global ingress traffic throttling.
 * Shields backend clusters from DDoS spikes, scraper bursts, and runaway microservice loops.
 */
@Service
public class RateLimiterService {

    private static final Logger log = LoggerFactory.getLogger(RateLimiterService.class);

    private final AtomicBoolean enabled = new AtomicBoolean(true);
    private final AtomicInteger capacity = new AtomicInteger(60);
    private final AtomicInteger refillRatePerMinute = new AtomicInteger(60);

    private final AtomicLong totalAllowedRequests = new AtomicLong(0);
    private final AtomicLong totalBlockedRequests = new AtomicLong(0);

    private final Map<String, TokenBucket> ipBuckets = new ConcurrentHashMap<>();

    public RateLimiterService(FluxGateProperties properties) {
        if (properties.getRateLimit() != null) {
            this.enabled.set(properties.getRateLimit().isEnabled());
            this.capacity.set(Math.max(1, properties.getRateLimit().getCapacity()));
            this.refillRatePerMinute.set(Math.max(1, properties.getRateLimit().getRefillRatePerMinute()));
        }
        log.info("[RATE-LIMITER] Initialized: enabled={}, capacity={}, refillRatePerMinute={}",
                enabled.get(), capacity.get(), refillRatePerMinute.get());
    }

    /**
     * Evaluates whether an incoming client request is permitted under token bucket rules.
     *
     * @param clientIp Client IP address (from X-Forwarded-For or socket remoteAddr)
     * @return RateLimitResult indicating pass/fail status and token telemetry
     */
    public RateLimitResult tryConsume(String clientIp) {
        String key = (clientIp != null && !clientIp.isBlank()) ? clientIp.trim() : "anonymous";

        if (!enabled.get()) {
            totalAllowedRequests.incrementAndGet();
            return new RateLimitResult(true, capacity.get(), capacity.get(), 0, key);
        }

        int currentCapacity = capacity.get();
        double refillRatePerSec = (double) refillRatePerMinute.get() / 60.0;
        double refillRatePerNano = refillRatePerSec / 1_000_000_000.0;

        TokenBucket bucket = ipBuckets.computeIfAbsent(key, k -> new TokenBucket(currentCapacity));
        boolean consumed = bucket.tryConsume(1.0, currentCapacity, refillRatePerNano);

        if (consumed) {
            totalAllowedRequests.incrementAndGet();
            long remaining = Math.max(0, (long) bucket.getTokens());
            return new RateLimitResult(true, remaining, currentCapacity, 0, key);
        } else {
            totalBlockedRequests.incrementAndGet();
            long resetSeconds = Math.max(1, Math.round(1.0 / refillRatePerSec));
            log.warn("[RATE-LIMIT EXCEEDED] IP={} rejected. Capacity={} Refill={}/min",
                    key, currentCapacity, refillRatePerMinute.get());
            return new RateLimitResult(false, 0, currentCapacity, resetSeconds, key);
        }
    }

    public boolean isEnabled() {
        return enabled.get();
    }

    public void setEnabled(boolean value) {
        this.enabled.set(value);
        log.info("[RATE-LIMITER] Rate limiter enabled state set to: {}", value);
    }

    public int getCapacity() {
        return capacity.get();
    }

    public void setCapacity(int newCapacity) {
        this.capacity.set(Math.max(1, newCapacity));
        log.info("[RATE-LIMITER] Burst capacity updated to: {}", this.capacity.get());
    }

    public int getRefillRatePerMinute() {
        return refillRatePerMinute.get();
    }

    public void setRefillRatePerMinute(int newRefillRate) {
        this.refillRatePerMinute.set(Math.max(1, newRefillRate));
        log.info("[RATE-LIMITER] Refill rate updated to: {}/min", this.refillRatePerMinute.get());
    }

    public long getTotalAllowedRequests() {
        return totalAllowedRequests.get();
    }

    public long getTotalBlockedRequests() {
        return totalBlockedRequests.get();
    }

    public int getTrackedIpCount() {
        return ipBuckets.size();
    }

    public void resetBuckets() {
        ipBuckets.clear();
        log.info("[RATE-LIMITER] Cleared all active client IP buckets");
    }

    /**
     * Individual Token Bucket tracking refill and token consumption for one client IP.
     */
    private static class TokenBucket {
        private double tokens;
        private long lastRefillNanos;

        public TokenBucket(int initialCapacity) {
            this.tokens = initialCapacity;
            this.lastRefillNanos = System.nanoTime();
        }

        public synchronized boolean tryConsume(double cost, int maxCapacity, double refillRatePerNano) {
            long now = System.nanoTime();
            long elapsedNanos = now - lastRefillNanos;
            if (elapsedNanos > 0) {
                double delta = elapsedNanos * refillRatePerNano;
                tokens = Math.min(maxCapacity, tokens + delta);
                lastRefillNanos = now;
            }

            if (tokens >= cost) {
                tokens -= cost;
                return true;
            }
            return false;
        }

        public synchronized double getTokens() {
            return tokens;
        }
    }
}

