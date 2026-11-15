package com.fluxgate.service;

import com.fluxgate.config.FluxGateProperties;
import com.fluxgate.model.ProxyResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class RequestCoalescingServiceTest {

    private RequestCoalescingService service;
    private FluxGateProperties properties;

    @BeforeEach
    void setUp() {
        properties = new FluxGateProperties();
        properties.getCoalescing().setEnabled(true);
        properties.getCoalescing().setTimeoutMs(5000);
        service = new RequestCoalescingService(properties);
    }

    @Test
    @DisplayName("Should execute independently when no concurrent contention")
    void testSequentialExecution() {
        ProxyResponse resp = new ProxyResponse(200, new HttpHeaders(), "ok".getBytes(), 10);
        RequestCoalescingService.CoalescedResult result = service.execute("GET:/test", () ->
                new RequestCoalescingService.CoalescedExecutionResult(resp, "http://localhost:8081", 0, null)
        );

        assertNotNull(result);
        assertFalse(result.isCoalesced());
        assertEquals(0, result.getWaiterCount());
        assertEquals(200, result.getResponse().getStatusCode());
        assertEquals(1, service.getTotalInitiatedRequests());
        assertEquals(0, service.getTotalCoalescedRequests());
    }

    @Test
    @DisplayName("Should coalesce concurrent duplicate in-flight requests into ONE execution")
    void testConcurrentCoalescing() throws Exception {
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicInteger upstreamExecutionCount = new AtomicInteger(0);

        List<Future<RequestCoalescingService.CoalescedResult>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                readyLatch.countDown();
                startLatch.await(); // ensure simultaneous trigger

                return service.execute("GET:/concurrent-resource", () -> {
                    upstreamExecutionCount.incrementAndGet();
                    try {
                        Thread.sleep(100); // Simulate network latency to allow peers to join
                    } catch (InterruptedException ignored) {}
                    ProxyResponse resp = new ProxyResponse(200, new HttpHeaders(), "payload".getBytes(), 100);
                    return new RequestCoalescingService.CoalescedExecutionResult(resp, "http://localhost:8081", 0, null);
                });
            }));
        }

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown();

        int coalescedCount = 0;
        int initiatorCount = 0;

        for (Future<RequestCoalescingService.CoalescedResult> future : futures) {
            RequestCoalescingService.CoalescedResult res = future.get(5, TimeUnit.SECONDS);
            assertNotNull(res);
            assertEquals(200, res.getResponse().getStatusCode());
            if (res.isCoalesced()) {
                coalescedCount++;
            } else {
                initiatorCount++;
            }
        }

        executor.shutdown();

        // Exactly ONE thread should have executed the upstream call
        assertEquals(1, upstreamExecutionCount.get(), "Upstream action must execute exactly once");
        assertEquals(1, initiatorCount, "Exactly 1 initiator expected");
        assertEquals(threadCount - 1, coalescedCount, "Remaining threads must be coalesced waiters");

        Map<String, Object> stats = service.getStats();
        assertTrue((Long) stats.get("upstreamCallsSaved") >= (threadCount - 1));
        assertEquals(0, stats.get("activeInFlightKeys"));
    }

    @Test
    @DisplayName("Should propagate upstream exceptions to all coalesced waiting callers")
    void testExceptionPropagation() throws Exception {
        int threadCount = 4;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);

        List<Future<RequestCoalescingService.CoalescedResult>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                readyLatch.countDown();
                startLatch.await();

                return service.execute("GET:/failing-resource", () -> {
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException ignored) {}
                    throw new IllegalStateException("Upstream connection reset");
                });
            }));
        }

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown();

        for (Future<RequestCoalescingService.CoalescedResult> future : futures) {
            ExecutionException ex = assertThrows(ExecutionException.class, () -> future.get(5, TimeUnit.SECONDS));
            assertTrue(ex.getCause() instanceof IllegalStateException || ex.getCause().getMessage().contains("Upstream connection reset"));
        }

        executor.shutdown();
        assertEquals(0, service.getActiveInFlightKeys());
    }

    @Test
    @DisplayName("Should bypass coalescing when disabled")
    void testDisabledCoalescing() {
        service.setEnabled(false);
        AtomicInteger callCount = new AtomicInteger(0);

        ProxyResponse resp = new ProxyResponse(200, new HttpHeaders(), "ok".getBytes(), 10);
        RequestCoalescingService.CoalescedResult r1 = service.execute("GET:/disabled", () -> {
            callCount.incrementAndGet();
            return new RequestCoalescingService.CoalescedExecutionResult(resp, "http://localhost:8081", 0, null);
        });

        RequestCoalescingService.CoalescedResult r2 = service.execute("GET:/disabled", () -> {
            callCount.incrementAndGet();
            return new RequestCoalescingService.CoalescedExecutionResult(resp, "http://localhost:8081", 0, null);
        });

        assertEquals(2, callCount.get());
        assertFalse(r1.isCoalesced());
        assertFalse(r2.isCoalesced());
    }
}
