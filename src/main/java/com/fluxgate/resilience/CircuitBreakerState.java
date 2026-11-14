package com.fluxgate.resilience;

/**
 * State of a backend circuit breaker.
 */
public enum CircuitBreakerState {
    /**
     * Normal operation: requests pass through to the backend.
     */
    CLOSED,

    /**
     * Tripped: requests are blocked and failed over immediately without waiting for timeouts.
     */
    OPEN,

    /**
     * Probing: recovery sleep window has passed; trial requests are allowed to test backend health.
     */
    HALF_OPEN
}
