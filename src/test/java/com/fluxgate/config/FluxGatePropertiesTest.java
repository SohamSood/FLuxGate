package com.fluxgate.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FluxGatePropertiesTest {

    @Test
    @DisplayName("Should verify default configuration properties across all modules")
    void testDefaultProperties() {
        FluxGateProperties props = new FluxGateProperties();

        assertNotNull(props.getBackends());
        assertEquals(3, props.getBackends().size());

        // Health Check
        assertTrue(props.getHealthCheck().isEnabled());
        assertEquals(10000L, props.getHealthCheck().getIntervalMs());
        assertEquals(2000, props.getHealthCheck().getTimeoutMs());
        assertEquals("/health", props.getHealthCheck().getHealthPath());

        // Proxy
        assertEquals(3000, props.getProxy().getConnectTimeoutMs());
        assertEquals(10000, props.getProxy().getReadTimeoutMs());

        // Demo Backends
        assertTrue(props.getDemoBackends().isEnabled());

        // Rate Limit
        assertTrue(props.getRateLimit().isEnabled());
        assertEquals(60, props.getRateLimit().getCapacity());
        assertEquals(60, props.getRateLimit().getRefillRatePerMinute());

        // Cache
        assertTrue(props.getCache().isEnabled());
        assertEquals(30, props.getCache().getDefaultTtlSeconds());
        assertEquals(500, props.getCache().getMaxEntries());

        // Circuit Breaker
        assertTrue(props.getCircuitBreaker().isEnabled());
        assertEquals(3, props.getCircuitBreaker().getFailureThreshold());
        assertEquals(15L, props.getCircuitBreaker().getRecoveryTimeoutSeconds());
        assertEquals(2, props.getCircuitBreaker().getHalfOpenTrialRequests());

        // Coalescing
        assertTrue(props.getCoalescing().isEnabled());
        assertEquals(10000L, props.getCoalescing().getTimeoutMs());

        // Security
        assertTrue(props.getSecurity().isEnabled());
        assertNotNull(props.getSecurity().getBlockedIps());
        assertNotNull(props.getSecurity().getAllowedIps());
    }

    @Test
    @DisplayName("Should allow mutation of properties")
    void testMutateProperties() {
        FluxGateProperties props = new FluxGateProperties();
        props.setBackends(List.of("http://test:8080"));
        assertEquals(List.of("http://test:8080"), props.getBackends());

        props.getProxy().setConnectTimeoutMs(1500);
        assertEquals(1500, props.getProxy().getConnectTimeoutMs());

        props.getHealthCheck().setIntervalMs(5000L);
        assertEquals(5000L, props.getHealthCheck().getIntervalMs());

        props.getSecurity().setBlockedIps(List.of("1.2.3.4"));
        assertEquals(List.of("1.2.3.4"), props.getSecurity().getBlockedIps());
    }
}
