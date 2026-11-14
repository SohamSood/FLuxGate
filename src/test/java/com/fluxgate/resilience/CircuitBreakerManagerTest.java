package com.fluxgate.resilience;

import com.fluxgate.config.FluxGateProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CircuitBreakerManagerTest {

    private CircuitBreakerManager manager;

    @BeforeEach
    void setUp() {
        FluxGateProperties props = new FluxGateProperties();
        props.getCircuitBreaker().setEnabled(true);
        props.getCircuitBreaker().setFailureThreshold(3);
        props.getCircuitBreaker().setRecoveryTimeoutSeconds(10);
        props.getCircuitBreaker().setHalfOpenTrialRequests(2);
        manager = new CircuitBreakerManager(props);
    }

    @Test
    @DisplayName("Creates circuit breaker on demand and allows requests by default")
    void managesPerBackendBreakers() {
        String url1 = "http://backend1:8081";
        String url2 = "http://backend2:8082";

        assertTrue(manager.allowRequest(url1));
        assertTrue(manager.allowRequest(url2));

        manager.trip(url1, "Chaos drill");
        assertFalse(manager.allowRequest(url1));
        assertTrue(manager.allowRequest(url2));

        manager.reset(url1);
        assertTrue(manager.allowRequest(url1));
    }

    @Test
    @DisplayName("Disabling circuit breaker manager allows all requests")
    void disableManagerBypassesChecks() {
        String url = "http://backend:8081";
        manager.trip(url, "Outage");
        assertFalse(manager.allowRequest(url));

        manager.setEnabled(false);
        assertTrue(manager.allowRequest(url));

        manager.setEnabled(true);
        assertFalse(manager.allowRequest(url));
    }

    @Test
    @DisplayName("Aggregates telemetry across multiple backends")
    void aggregatesTelemetry() {
        manager.getCircuitBreaker("http://b1:8081");
        manager.getCircuitBreaker("http://b2:8082");
        manager.trip("http://b1:8081", "Manual");
        manager.recordFailover("http://b1:8081");

        Map<String, Object> telemetry = manager.getTelemetry();
        assertTrue((boolean) telemetry.get("enabled"));
        assertEquals(1L, telemetry.get("openCircuits"));
        assertEquals(1L, telemetry.get("closedCircuits"));
        assertEquals(1, telemetry.get("totalFailovers"));
    }
}
