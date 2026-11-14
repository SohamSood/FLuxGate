package com.fluxgate.resilience;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Thread-safe Circuit Breaker implementation for an upstream backend.
 * Protects against cascading failures, isolates flaky hosts, and enables
 * zero-downtime automatic in-flight failovers.
 */
public class CircuitBreaker {

    private static final Logger log = LoggerFactory.getLogger(CircuitBreaker.class);

    private final String backendUrl;
    private final int failureThreshold;
    private final long recoveryTimeoutSeconds;
    private final int halfOpenTrialRequests;

    private final AtomicReference<CircuitBreakerState> state = new AtomicReference<>(CircuitBreakerState.CLOSED);
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private final AtomicInteger consecutiveSuccesses = new AtomicInteger(0);
    private final AtomicInteger halfOpenAttempts = new AtomicInteger(0);
    private final AtomicInteger totalTrips = new AtomicInteger(0);
    private final AtomicInteger totalFailovers = new AtomicInteger(0);

    private volatile Instant lastStateTransitionTime = Instant.now();
    private volatile Instant lastFailureTime;
    private volatile String lastFailureReason = "None";

    public CircuitBreaker(String backendUrl, int failureThreshold, long recoveryTimeoutSeconds, int halfOpenTrialRequests) {
        this.backendUrl = backendUrl;
        this.failureThreshold = Math.max(1, failureThreshold);
        this.recoveryTimeoutSeconds = Math.max(1, recoveryTimeoutSeconds);
        this.halfOpenTrialRequests = Math.max(1, halfOpenTrialRequests);
    }

    public String getBackendUrl() {
        return backendUrl;
    }

    public CircuitBreakerState getState() {
        checkStateTransition();
        return state.get();
    }

    /**
     * Checks if a request is permitted without consuming a trial attempt.
     */
    public boolean isRequestPermitted() {
        checkStateTransition();
        CircuitBreakerState current = state.get();
        if (current == CircuitBreakerState.CLOSED) {
            return true;
        }
        if (current == CircuitBreakerState.HALF_OPEN) {
            return halfOpenAttempts.get() < halfOpenTrialRequests;
        }
        return false;
    }

    /**
     * Attempts to acquire permission for an in-flight request. Consumes a trial attempt in HALF_OPEN.
     */
    public boolean allowRequest() {
        checkStateTransition();
        CircuitBreakerState current = state.get();

        if (current == CircuitBreakerState.CLOSED) {
            return true;
        }

        if (current == CircuitBreakerState.HALF_OPEN) {
            int attempt = halfOpenAttempts.incrementAndGet();
            return attempt <= halfOpenTrialRequests;
        }

        // OPEN state: block request and trigger immediate failover
        return false;
    }

    /**
     * Transitions OPEN -> HALF_OPEN once recovery timeout has passed.
     */
    private void checkStateTransition() {
        if (state.get() == CircuitBreakerState.OPEN && lastFailureTime != null) {
            long secondsSinceFailure = Instant.now().getEpochSecond() - lastFailureTime.getEpochSecond();
            if (secondsSinceFailure >= recoveryTimeoutSeconds) {
                if (state.compareAndSet(CircuitBreakerState.OPEN, CircuitBreakerState.HALF_OPEN)) {
                    lastStateTransitionTime = Instant.now();
                    halfOpenAttempts.set(0);
                    consecutiveSuccesses.set(0);
                    log.info("[CIRCUIT-BREAKER] Backend '{}' transitioned OPEN -> HALF_OPEN after {}s recovery sleep. Probing backend health.",
                            backendUrl, secondsSinceFailure);
                }
            }
        }
    }

    /**
     * Records a successful upstream response.
     */
    public void recordSuccess() {
        checkStateTransition();
        CircuitBreakerState current = state.get();

        if (current == CircuitBreakerState.HALF_OPEN) {
            int successes = consecutiveSuccesses.incrementAndGet();
            if (successes >= halfOpenTrialRequests) {
                if (state.compareAndSet(CircuitBreakerState.HALF_OPEN, CircuitBreakerState.CLOSED)) {
                    lastStateTransitionTime = Instant.now();
                    consecutiveFailures.set(0);
                    consecutiveSuccesses.set(0);
                    halfOpenAttempts.set(0);
                    log.info("[CIRCUIT-BREAKER] Backend '{}' recovered! Transitioned HALF_OPEN -> CLOSED (all {} trial requests succeeded).",
                            backendUrl, halfOpenTrialRequests);
                }
            }
        } else if (current == CircuitBreakerState.CLOSED) {
            consecutiveFailures.set(0);
        }
    }

    /**
     * Records a failure (5xx status, connection error, or timeout).
     */
    public void recordFailure(String reason) {
        lastFailureTime = Instant.now();
        lastFailureReason = reason != null ? reason : "Unknown failure";
        int failures = consecutiveFailures.incrementAndGet();

        CircuitBreakerState current = state.get();

        if (current == CircuitBreakerState.HALF_OPEN) {
            // In trial mode, even 1 failure trips it back to OPEN
            if (state.compareAndSet(CircuitBreakerState.HALF_OPEN, CircuitBreakerState.OPEN)) {
                lastStateTransitionTime = Instant.now();
                totalTrips.incrementAndGet();
                log.warn("[CIRCUIT-BREAKER] Backend '{}' failed trial probe ({}). Transitioned HALF_OPEN -> OPEN.",
                        backendUrl, lastFailureReason);
            }
        } else if (current == CircuitBreakerState.CLOSED) {
            if (failures >= failureThreshold) {
                if (state.compareAndSet(CircuitBreakerState.CLOSED, CircuitBreakerState.OPEN)) {
                    lastStateTransitionTime = Instant.now();
                    totalTrips.incrementAndGet();
                    log.warn("[CIRCUIT-BREAKER] Backend '{}' tripped after {} consecutive failures ({}). State is now OPEN.",
                            backendUrl, failures, lastFailureReason);
                }
            }
        }
    }

    /**
     * Manually trips the circuit breaker to OPEN (e.g. for chaos testing or administrative quarantine).
     */
    public void trip(String reason) {
        state.set(CircuitBreakerState.OPEN);
        lastStateTransitionTime = Instant.now();
        lastFailureTime = Instant.now();
        lastFailureReason = reason != null ? reason : "Manual trip";
        totalTrips.incrementAndGet();
        log.warn("[CIRCUIT-BREAKER] Backend '{}' manually tripped to OPEN: {}", backendUrl, lastFailureReason);
    }

    /**
     * Manually resets the circuit breaker to CLOSED.
     */
    public void reset() {
        state.set(CircuitBreakerState.CLOSED);
        lastStateTransitionTime = Instant.now();
        consecutiveFailures.set(0);
        consecutiveSuccesses.set(0);
        halfOpenAttempts.set(0);
        lastFailureReason = "Reset to healthy";
        log.info("[CIRCUIT-BREAKER] Backend '{}' manually reset to CLOSED.", backendUrl);
    }

    /**
     * Increments the count of in-flight failovers rerouted from or through this backend.
     */
    public void incrementFailovers() {
        totalFailovers.incrementAndGet();
    }

    /**
     * Telemetry map for monitoring and dashboard.
     */
    public Map<String, Object> getTelemetry() {
        checkStateTransition();
        Map<String, Object> telemetry = new HashMap<>();
        CircuitBreakerState current = state.get();
        telemetry.put("backendUrl", backendUrl);
        telemetry.put("state", current.name());
        telemetry.put("isAllowed", isRequestPermitted());
        telemetry.put("consecutiveFailures", consecutiveFailures.get());
        telemetry.put("failureThreshold", failureThreshold);
        telemetry.put("recoveryTimeoutSeconds", recoveryTimeoutSeconds);
        telemetry.put("halfOpenTrialRequests", halfOpenTrialRequests);
        telemetry.put("totalTrips", totalTrips.get());
        telemetry.put("totalFailovers", totalFailovers.get());
        telemetry.put("lastFailureReason", lastFailureReason);
        telemetry.put("lastFailureTime", lastFailureTime != null ? lastFailureTime.toString() : null);
        telemetry.put("lastStateTransitionTime", lastStateTransitionTime.toString());
        return telemetry;
    }
}
