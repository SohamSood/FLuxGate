package com.fluxgate.resilience;

import com.fluxgate.config.FluxGateProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Service orchestrating Circuit Breakers across all upstream backends.
 * Central coordinator for health state, trip events, in-flight failovers, and telemetry.
 */
@Service
public class CircuitBreakerManager {

    private static final Logger log = LoggerFactory.getLogger(CircuitBreakerManager.class);

    private final FluxGateProperties properties;
    private final ConcurrentHashMap<String, CircuitBreaker> breakers = new ConcurrentHashMap<>();
    private final AtomicBoolean enabled = new AtomicBoolean(true);
    private final AtomicInteger globalFailovers = new AtomicInteger(0);

    public CircuitBreakerManager(FluxGateProperties properties) {
        this.properties = properties;
        this.enabled.set(properties.getCircuitBreaker().isEnabled());
        log.info("[CIRCUIT-BREAKER] Initialized manager: enabled={}, failureThreshold={}, recoveryTimeout={}s, trialProbes={}",
                enabled.get(),
                properties.getCircuitBreaker().getFailureThreshold(),
                properties.getCircuitBreaker().getRecoveryTimeoutSeconds(),
                properties.getCircuitBreaker().getHalfOpenTrialRequests());
    }

    public boolean isEnabled() {
        return enabled.get();
    }

    public void setEnabled(boolean value) {
        this.enabled.set(value);
        log.info("[CIRCUIT-BREAKER] Enabled status set to: {}", value);
    }

    public CircuitBreaker getCircuitBreaker(String backendUrl) {
        return breakers.computeIfAbsent(backendUrl, url -> new CircuitBreaker(
                url,
                properties.getCircuitBreaker().getFailureThreshold(),
                properties.getCircuitBreaker().getRecoveryTimeoutSeconds(),
                properties.getCircuitBreaker().getHalfOpenTrialRequests()
        ));
    }

    /**
     * Checks if a backend is allowed to receive traffic according to circuit state.
     */
    public boolean allowRequest(String backendUrl) {
        if (!isEnabled()) {
            return true;
        }
        return getCircuitBreaker(backendUrl).allowRequest();
    }

    public boolean isRequestPermitted(String backendUrl) {
        if (!isEnabled()) {
            return true;
        }
        return getCircuitBreaker(backendUrl).isRequestPermitted();
    }

    public void recordSuccess(String backendUrl) {
        if (isEnabled()) {
            getCircuitBreaker(backendUrl).recordSuccess();
        }
    }

    public void recordFailure(String backendUrl, String reason) {
        if (isEnabled()) {
            getCircuitBreaker(backendUrl).recordFailure(reason);
        }
    }

    public void recordFailover(String fromBackendUrl) {
        globalFailovers.incrementAndGet();
        if (fromBackendUrl != null) {
            getCircuitBreaker(fromBackendUrl).incrementFailovers();
        }
    }

    public void trip(String backendUrl, String reason) {
        getCircuitBreaker(backendUrl).trip(reason);
    }

    public void reset(String backendUrl) {
        getCircuitBreaker(backendUrl).reset();
    }

    public void resetAll() {
        breakers.values().forEach(CircuitBreaker::reset);
        log.info("[CIRCUIT-BREAKER] All registered circuit breakers reset to CLOSED.");
    }

    public int getGlobalFailovers() {
        return globalFailovers.get();
    }

    public Collection<CircuitBreaker> getAllBreakers() {
        return breakers.values();
    }

    /**
     * Full telemetry for administration and dashboard.
     */
    public Map<String, Object> getTelemetry() {
        Map<String, Object> summary = new HashMap<>();
        summary.put("enabled", isEnabled());
        summary.put("failureThreshold", properties.getCircuitBreaker().getFailureThreshold());
        summary.put("recoveryTimeoutSeconds", properties.getCircuitBreaker().getRecoveryTimeoutSeconds());
        summary.put("halfOpenTrialRequests", properties.getCircuitBreaker().getHalfOpenTrialRequests());
        summary.put("totalFailovers", globalFailovers.get());

        Map<String, Object> backendStatuses = new HashMap<>();
        long openCount = 0;
        long halfOpenCount = 0;
        long closedCount = 0;

        for (Map.Entry<String, CircuitBreaker> entry : breakers.entrySet()) {
            Map<String, Object> cbData = entry.getValue().getTelemetry();
            backendStatuses.put(entry.getKey(), cbData);
            String st = (String) cbData.get("state");
            if ("OPEN".equals(st)) openCount++;
            else if ("HALF_OPEN".equals(st)) halfOpenCount++;
            else closedCount++;
        }

        summary.put("openCircuits", openCount);
        summary.put("halfOpenCircuits", halfOpenCount);
        summary.put("closedCircuits", closedCount);
        summary.put("backends", backendStatuses);
        return summary;
    }
}
