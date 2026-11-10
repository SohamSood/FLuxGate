package com.fluxgate.strategy;

import com.fluxgate.model.BackendServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LeastConnectionsStrategyTest {

    private LeastConnectionsStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new LeastConnectionsStrategy();
    }

    @Test
    @DisplayName("Should return empty when backends list is empty or null")
    void testEmptyBackends() {
        assertTrue(strategy.select(null).isEmpty());
        assertTrue(strategy.select(Collections.emptyList()).isEmpty());
    }

    @Test
    @DisplayName("Should select backend with fewest active in-flight requests")
    void testSelectLeastActive() {
        BackendServer b1 = new BackendServer("http://localhost:8081");
        BackendServer b2 = new BackendServer("http://localhost:8082");
        BackendServer b3 = new BackendServer("http://localhost:8083");

        b1.incrementRequests();
        b1.incrementRequests(); // active = 2

        b2.incrementRequests(); // active = 1

        // b3 active = 0

        List<BackendServer> backends = List.of(b1, b2, b3);

        assertEquals("http://localhost:8083", strategy.select(backends).orElseThrow().getUrl());
    }

    @Test
    @DisplayName("Should skip unhealthy backends even if they have 0 active connections")
    void testSkipUnhealthy() {
        BackendServer b1 = new BackendServer("http://localhost:8081");
        BackendServer b2 = new BackendServer("http://localhost:8082");

        b1.markUnhealthy("Down");
        b2.incrementRequests(); // active = 1

        List<BackendServer> backends = List.of(b1, b2);

        assertEquals("http://localhost:8082", strategy.select(backends).orElseThrow().getUrl());
    }
}
