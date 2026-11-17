package com.fluxgate.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BackendServerTest {

    @Test
    @DisplayName("Should initialize backend with default healthy state and weight")
    void testInitialization() {
        BackendServer server = new BackendServer("http://localhost:8081");

        assertEquals("http://localhost:8081", server.getUrl());
        assertTrue(server.isHealthy());
        assertEquals(1, server.getWeight());
        assertEquals(0, server.getActiveRequests());
        assertEquals(0, server.getTotalRequests());
    }

    @Test
    @DisplayName("Should sanitize trailing slashes from URL")
    void testUrlSanitization() {
        BackendServer server1 = new BackendServer("http://localhost:8081/");
        assertEquals("http://localhost:8081", server1.getUrl());

        BackendServer server2 = new BackendServer("http://api.example.com///");
        assertEquals("http://api.example.com", server2.getUrl());

        assertEquals("http://host:9000", BackendServer.sanitizeUrl("  http://host:9000/  "));
    }

    @Test
    @DisplayName("Should reject null URL during sanitization")
    void testNullUrlThrowsException() {
        assertThrows(NullPointerException.class, () -> new BackendServer(null));
    }

    @Test
    @DisplayName("Should transition between healthy and unhealthy states")
    void testHealthTransitions() {
        BackendServer server = new BackendServer("http://localhost:8081");

        server.markUnhealthy("Connection refused");
        assertFalse(server.isHealthy());
        assertEquals("Connection refused", server.getLastFailureReason());
        assertEquals(1, server.getConsecutiveFailures());
        assertEquals(0, server.getConsecutiveSuccesses());

        server.markHealthy();
        assertTrue(server.isHealthy());
        assertNull(server.getLastFailureReason());
        assertEquals(0, server.getConsecutiveFailures());
        assertEquals(1, server.getConsecutiveSuccesses());
    }

    @Test
    @DisplayName("Should track active and total request counts")
    void testRequestCounters() {
        BackendServer server = new BackendServer("http://localhost:8081");

        server.incrementRequests();
        server.incrementRequests();
        assertEquals(2, server.getActiveRequests());
        assertEquals(2, server.getTotalRequests());

        server.decrementActiveRequests();
        assertEquals(1, server.getActiveRequests());
        assertEquals(2, server.getTotalRequests());

        server.decrementActiveRequests();
        assertEquals(0, server.getActiveRequests());
    }

    @Test
    @DisplayName("Should update server weight within bounds")
    void testWeight() {
        BackendServer server = new BackendServer("http://localhost:8081", true, 5);
        assertEquals(5, server.getWeight());

        server.setWeight(10);
        assertEquals(10, server.getWeight());

        // Weight less than 1 defaults to 1
        server.setWeight(-3);
        assertEquals(1, server.getWeight());
    }
}
