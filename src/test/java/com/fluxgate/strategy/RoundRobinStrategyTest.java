package com.fluxgate.strategy;

import com.fluxgate.model.BackendServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class RoundRobinStrategyTest {

    private RoundRobinStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new RoundRobinStrategy();
    }

    @Test
    @DisplayName("Should return empty when backend list is empty or null")
    void testEmptyOrNullBackends() {
        assertTrue(strategy.select(null).isEmpty());
        assertTrue(strategy.select(Collections.emptyList()).isEmpty());
    }

    @Test
    @DisplayName("Should rotate sequentially across healthy backends")
    void testSequentialRotation() {
        BackendServer b1 = new BackendServer("http://localhost:8081");
        BackendServer b2 = new BackendServer("http://localhost:8082");
        BackendServer b3 = new BackendServer("http://localhost:8083");
        List<BackendServer> backends = List.of(b1, b2, b3);

        assertEquals("http://localhost:8081", strategy.select(backends).orElseThrow().getUrl());
        assertEquals("http://localhost:8082", strategy.select(backends).orElseThrow().getUrl());
        assertEquals("http://localhost:8083", strategy.select(backends).orElseThrow().getUrl());
        assertEquals("http://localhost:8081", strategy.select(backends).orElseThrow().getUrl());
        assertEquals("http://localhost:8082", strategy.select(backends).orElseThrow().getUrl());
    }

    @Test
    @DisplayName("Should skip unhealthy backends")
    void testSkipUnhealthyBackends() {
        BackendServer b1 = new BackendServer("http://localhost:8081");
        BackendServer b2 = new BackendServer("http://localhost:8082");
        BackendServer b3 = new BackendServer("http://localhost:8083");
        List<BackendServer> backends = List.of(b1, b2, b3);

        b2.markUnhealthy("Connection failed");

        assertEquals("http://localhost:8081", strategy.select(backends).orElseThrow().getUrl());
        assertEquals("http://localhost:8083", strategy.select(backends).orElseThrow().getUrl());
        assertEquals("http://localhost:8081", strategy.select(backends).orElseThrow().getUrl());
        assertEquals("http://localhost:8083", strategy.select(backends).orElseThrow().getUrl());
    }

    @Test
    @DisplayName("Should return empty when all backends are unhealthy")
    void testAllUnhealthyBackends() {
        BackendServer b1 = new BackendServer("http://localhost:8081");
        BackendServer b2 = new BackendServer("http://localhost:8082");
        b1.markUnhealthy("down");
        b2.markUnhealthy("down");

        Optional<BackendServer> result = strategy.select(List.of(b1, b2));
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Should be thread-safe under concurrent selections")
    void testConcurrentSelections() throws InterruptedException {
        BackendServer b1 = new BackendServer("http://localhost:8081");
        BackendServer b2 = new BackendServer("http://localhost:8082");
        BackendServer b3 = new BackendServer("http://localhost:8083");
        List<BackendServer> backends = List.of(b1, b2, b3);

        int threadCount = 20;
        int requestsPerThread = 300;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        ConcurrentMap<String, AtomicInteger> counts = new ConcurrentHashMap<>();
        counts.put(b1.getUrl(), new AtomicInteger(0));
        counts.put(b2.getUrl(), new AtomicInteger(0));
        counts.put(b3.getUrl(), new AtomicInteger(0));

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < requestsPerThread; j++) {
                        BackendServer selected = strategy.select(backends).orElseThrow();
                        counts.get(selected.getUrl()).incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        executor.shutdown();

        int total = threadCount * requestsPerThread;
        int sum = counts.values().stream().mapToInt(AtomicInteger::get).sum();
        assertEquals(total, sum);

        // Each backend should receive approximately 1/3 of the requests
        int expectedPerBackend = total / 3;
        for (AtomicInteger count : counts.values()) {
            assertEquals(expectedPerBackend, count.get(), 5);
        }
    }
}
