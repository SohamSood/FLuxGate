package com.fluxgate.strategy;

import com.fluxgate.model.BackendServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class RandomStrategyTest {

    private RandomStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new RandomStrategy();
    }

    @Test
    @DisplayName("Should return empty when backends list is null or empty")
    void testEmptyList() {
        assertTrue(strategy.select(null).isEmpty());
        assertTrue(strategy.select(Collections.emptyList()).isEmpty());
    }

    @Test
    @DisplayName("Should return empty when all backends are unhealthy")
    void testAllUnhealthy() {
        BackendServer b1 = new BackendServer("http://localhost:8081");
        b1.markUnhealthy("down");
        BackendServer b2 = new BackendServer("http://localhost:8082");
        b2.markUnhealthy("down");

        assertTrue(strategy.select(List.of(b1, b2)).isEmpty());
    }

    @Test
    @DisplayName("Should select one of the healthy backends at random")
    void testSelectsHealthy() {
        BackendServer b1 = new BackendServer("http://localhost:8081");
        BackendServer b2 = new BackendServer("http://localhost:8082");
        b2.markUnhealthy("down");
        BackendServer b3 = new BackendServer("http://localhost:8083");

        for (int i = 0; i < 20; i++) {
            Optional<BackendServer> selected = strategy.select(List.of(b1, b2, b3));
            assertTrue(selected.isPresent());
            assertNotEquals("http://localhost:8082", selected.get().getUrl());
            assertTrue(selected.get().getUrl().equals("http://localhost:8081") ||
                    selected.get().getUrl().equals("http://localhost:8083"));
        }
    }

    @Test
    @DisplayName("Should return strategy name RANDOM")
    void testStrategyName() {
        assertEquals("RANDOM", strategy.getName());
    }
}
