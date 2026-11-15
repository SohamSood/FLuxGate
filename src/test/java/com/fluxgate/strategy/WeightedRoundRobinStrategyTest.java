package com.fluxgate.strategy;

import com.fluxgate.model.BackendServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class WeightedRoundRobinStrategyTest {

    private WeightedRoundRobinStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new WeightedRoundRobinStrategy();
    }

    @Test
    @DisplayName("Should return empty optional for empty or null backends")
    void testEmptyBackends() {
        assertTrue(strategy.select(null).isEmpty());
        assertTrue(strategy.select(Collections.emptyList()).isEmpty());
    }

    @Test
    @DisplayName("Should select the only backend if single candidate")
    void testSingleBackend() {
        BackendServer server = new BackendServer("http://localhost:8081", true, 3);
        Optional<BackendServer> selected = strategy.select(List.of(server));

        assertTrue(selected.isPresent());
        assertEquals("http://localhost:8081", selected.get().getUrl());
    }

    @Test
    @DisplayName("Should distribute requests according to configured weights (5:1:1)")
    void testWeightedDistribution() {
        BackendServer serverA = new BackendServer("http://localhost:8081", true, 5);
        BackendServer serverB = new BackendServer("http://localhost:8082", true, 1);
        BackendServer serverC = new BackendServer("http://localhost:8083", true, 1);

        List<BackendServer> cluster = List.of(serverA, serverB, serverC);
        Map<String, Integer> counts = new HashMap<>();

        // Run 7 requests (5 + 1 + 1)
        for (int i = 0; i < 7; i++) {
            BackendServer selected = strategy.select(cluster).orElseThrow();
            counts.put(selected.getUrl(), counts.getOrDefault(selected.getUrl(), 0) + 1);
        }

        assertEquals(5, counts.get("http://localhost:8081"));
        assertEquals(1, counts.get("http://localhost:8082"));
        assertEquals(1, counts.get("http://localhost:8083"));
    }

    @Test
    @DisplayName("Should distribute requests smoothly and evenly (3:2:1)")
    void testSmoothInterleavedDistribution() {
        BackendServer serverA = new BackendServer("http://localhost:8081", true, 3);
        BackendServer serverB = new BackendServer("http://localhost:8082", true, 2);
        BackendServer serverC = new BackendServer("http://localhost:8083", true, 1);

        List<BackendServer> cluster = List.of(serverA, serverB, serverC);
        Map<String, Integer> counts = new HashMap<>();

        // 60 requests -> ratio should be 30:20:10
        for (int i = 0; i < 60; i++) {
            BackendServer selected = strategy.select(cluster).orElseThrow();
            counts.put(selected.getUrl(), counts.getOrDefault(selected.getUrl(), 0) + 1);
        }

        assertEquals(30, counts.get("http://localhost:8081"));
        assertEquals(20, counts.get("http://localhost:8082"));
        assertEquals(10, counts.get("http://localhost:8083"));
    }

    @Test
    @DisplayName("Should verify strategy name")
    void testStrategyName() {
        assertEquals("WEIGHTED_ROUND_ROBIN", strategy.getName());
    }
}
