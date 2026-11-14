package com.fluxgate.resilience;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CircuitBreakerTest {

    private CircuitBreaker circuitBreaker;

    @BeforeEach
    void setUp() {
        // threshold = 3 failures, recovery timeout = 2 seconds, trial requests = 2
        circuitBreaker = new CircuitBreaker("http://localhost:8081", 3, 2, 2);
    }

    @Test
    @DisplayName("Initial state should be CLOSED and permit requests")
    void initialStateIsClosed() {
        assertEquals(CircuitBreakerState.CLOSED, circuitBreaker.getState());
        assertTrue(circuitBreaker.allowRequest());
        assertTrue(circuitBreaker.isRequestPermitted());
    }

    @Test
    @DisplayName("Transitions CLOSED -> OPEN after consecutive failures exceed threshold")
    void transitionsToOpenOnFailures() {
        circuitBreaker.recordFailure("500 Internal Server Error");
        assertEquals(CircuitBreakerState.CLOSED, circuitBreaker.getState());
        assertTrue(circuitBreaker.allowRequest());

        circuitBreaker.recordFailure("502 Bad Gateway");
        assertEquals(CircuitBreakerState.CLOSED, circuitBreaker.getState());
        assertTrue(circuitBreaker.allowRequest());

        circuitBreaker.recordFailure("504 Gateway Timeout");
        assertEquals(CircuitBreakerState.OPEN, circuitBreaker.getState());
        assertFalse(circuitBreaker.allowRequest());
        assertFalse(circuitBreaker.isRequestPermitted());
    }

    @Test
    @DisplayName("Success in CLOSED state resets failure count")
    void successResetsFailureCount() {
        circuitBreaker.recordFailure("Error 1");
        circuitBreaker.recordFailure("Error 2");
        assertEquals(2, (int) circuitBreaker.getTelemetry().get("consecutiveFailures"));

        circuitBreaker.recordSuccess();
        assertEquals(0, (int) circuitBreaker.getTelemetry().get("consecutiveFailures"));
        assertEquals(CircuitBreakerState.CLOSED, circuitBreaker.getState());
    }

    @Test
    @DisplayName("Manual trip immediately sets state to OPEN and blocks requests")
    void manualTripAndReset() {
        circuitBreaker.trip("Operator chaos drill");
        assertEquals(CircuitBreakerState.OPEN, circuitBreaker.getState());
        assertFalse(circuitBreaker.allowRequest());

        circuitBreaker.reset();
        assertEquals(CircuitBreakerState.CLOSED, circuitBreaker.getState());
        assertTrue(circuitBreaker.allowRequest());
    }

    @Test
    @DisplayName("Transitions OPEN -> HALF_OPEN after recovery timeout, then back to CLOSED on successful trials")
    void recoveryFlowToClosed() throws InterruptedException {
        // Trip circuit
        circuitBreaker.trip("Test trip");
        assertEquals(CircuitBreakerState.OPEN, circuitBreaker.getState());
        assertFalse(circuitBreaker.allowRequest());

        // Wait for recovery timeout (2 seconds)
        Thread.sleep(2100);

        // First trial request in HALF_OPEN should be permitted
        assertTrue(circuitBreaker.allowRequest());
        assertEquals(CircuitBreakerState.HALF_OPEN, circuitBreaker.getState());

        // Record first success
        circuitBreaker.recordSuccess();
        assertEquals(CircuitBreakerState.HALF_OPEN, circuitBreaker.getState());

        // Record second success (trial threshold reached) -> transitions to CLOSED
        circuitBreaker.recordSuccess();
        assertEquals(CircuitBreakerState.CLOSED, circuitBreaker.getState());
        assertTrue(circuitBreaker.allowRequest());
    }

    @Test
    @DisplayName("In HALF_OPEN state, single failure trips back to OPEN immediately")
    void halfOpenFailsBackToOpen() throws InterruptedException {
        circuitBreaker.trip("Test trip");
        Thread.sleep(2100);

        // Probe trial
        assertTrue(circuitBreaker.allowRequest());
        assertEquals(CircuitBreakerState.HALF_OPEN, circuitBreaker.getState());

        // Failure during trial
        circuitBreaker.recordFailure("Trial probe connection refused");
        assertEquals(CircuitBreakerState.OPEN, circuitBreaker.getState());
        assertFalse(circuitBreaker.allowRequest());
    }

    @Test
    @DisplayName("Telemetry returns expected status and metrics")
    void telemetryOutputsExpectedFields() {
        circuitBreaker.recordFailure("Test error");
        circuitBreaker.incrementFailovers();

        Map<String, Object> telemetry = circuitBreaker.getTelemetry();
        assertEquals("http://localhost:8081", telemetry.get("backendUrl"));
        assertEquals("CLOSED", telemetry.get("state"));
        assertEquals(1, telemetry.get("consecutiveFailures"));
        assertEquals(1, telemetry.get("totalFailovers"));
        assertEquals("Test error", telemetry.get("lastFailureReason"));
    }
}
