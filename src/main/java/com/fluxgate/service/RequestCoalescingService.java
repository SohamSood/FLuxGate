package com.fluxgate.service;

import com.fluxgate.config.FluxGateProperties;
import com.fluxgate.model.ProxyResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * SingleFlight Concurrency Deduplicator & Thundering Herd Protector.
 *
 * When high concurrency surges or cache stampedes occur (e.g. multiple clients requesting
 * the same resource on a cold cache or traffic burst), this service coalesces all identical
 * concurrent in-flight requests into ONE upstream execution.
 *
 * Subsequent concurrent callers attach as listeners to the in-flight future and safely share
 * the exact same response upon completion. This slashes upstream backend load by up to 99%,
 * conserves socket buffers, eliminates thread pool exhaustion, and radically scales throughput.
 */
@Service
public class RequestCoalescingService {

    private static final Logger log = LoggerFactory.getLogger(RequestCoalescingService.class);

    private final ConcurrentHashMap<String, InFlightGroup> inFlight = new ConcurrentHashMap<>();

    private final AtomicLong totalCoalescedRequests = new AtomicLong(0);
    private final AtomicLong totalInitiatedRequests = new AtomicLong(0);
    private final AtomicLong upstreamCallsSaved = new AtomicLong(0);

    private volatile boolean enabled = true;
    private volatile long timeoutMs = 10000;

    public RequestCoalescingService(FluxGateProperties properties) {
        if (properties.getCoalescing() != null) {
            this.enabled = properties.getCoalescing().isEnabled();
            this.timeoutMs = properties.getCoalescing().getTimeoutMs();
        }
        log.info("[COALESCING] Initialized SingleFlight accelerator: enabled={}, timeoutMs={}", enabled, timeoutMs);
    }

    /**
     * Executes or joins an in-flight request execution for the specified resource key.
     */
    public CoalescedResult execute(String key, Supplier<CoalescedExecutionResult> action) {
        if (!enabled || key == null || key.isBlank()) {
            CoalescedExecutionResult res = action.get();
            return new CoalescedResult(res.getResponse(), false, 0, res.getOriginBackend(),
                    res.getFailoverAttempts(), res.getFailoverOrigin());
        }

        InFlightGroup group = inFlight.get(key);
        if (group != null) {
            // Join existing in-flight execution as a waiter
            return awaitInFlightGroup(key, group, action);
        }

        // Attempt to register as the initiator (primary leader)
        InFlightGroup newGroup = new InFlightGroup();
        InFlightGroup existing = inFlight.putIfAbsent(key, newGroup);
        if (existing != null) {
            return awaitInFlightGroup(key, existing, action);
        }

        // We are the initiator
        totalInitiatedRequests.incrementAndGet();
        try {
            CoalescedExecutionResult result = action.get();
            newGroup.future.complete(result);
            int waiters = newGroup.getWaiterCount();
            if (waiters > 0) {
                log.info("[COALESCE FULFILLED] Key '{}' completed. Upstream response broadcasted to {} waiting callers.",
                        key, waiters);
            }
            return new CoalescedResult(result.getResponse(), false, waiters,
                    result.getOriginBackend(), result.getFailoverAttempts(), result.getFailoverOrigin());
        } catch (Throwable t) {
            newGroup.future.completeExceptionally(t);
            if (t instanceof RuntimeException re) throw re;
            throw new RuntimeException("Upstream execution error during coalescing", t);
        } finally {
            inFlight.remove(key, newGroup);
        }
    }

    private CoalescedResult awaitInFlightGroup(String key, InFlightGroup group,
                                              Supplier<CoalescedExecutionResult> fallbackAction) {
        group.waiterCount.incrementAndGet();
        totalCoalescedRequests.incrementAndGet();
        upstreamCallsSaved.incrementAndGet();

        log.debug("[COALESCE JOIN] Attaching concurrent request to in-flight key: {}", key);

        try {
            CoalescedExecutionResult execResult = group.future.get(timeoutMs, TimeUnit.MILLISECONDS);
            long waiterLatency = System.currentTimeMillis() - group.startTime;

            ProxyResponse sharedResponse = new ProxyResponse(
                    execResult.getResponse().getStatusCode(),
                    execResult.getResponse().getHeaders(),
                    execResult.getResponse().getBody(),
                    waiterLatency
            );

            return new CoalescedResult(
                    sharedResponse,
                    true,
                    group.getWaiterCount(),
                    execResult.getOriginBackend(),
                    execResult.getFailoverAttempts(),
                    execResult.getFailoverOrigin()
            );
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException re) throw re;
            throw new RuntimeException("Coalesced upstream in-flight request failed: " + cause.getMessage(), cause);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Coalesced request waiting thread was interrupted", e);
        } catch (TimeoutException e) {
            log.warn("[COALESCE TIMEOUT] Waiting on in-flight key '{}' timed out after {}ms. Executing fallback request.",
                    key, timeoutMs);
            CoalescedExecutionResult res = fallbackAction.get();
            return new CoalescedResult(res.getResponse(), false, 0, res.getOriginBackend(),
                    res.getFailoverAttempts(), res.getFailoverOrigin());
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        log.info("[COALESCING] Enabled state changed to: {}", enabled);
    }

    public long getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(long timeoutMs) {
        this.timeoutMs = Math.max(100, timeoutMs);
    }

    public long getTotalCoalescedRequests() {
        return totalCoalescedRequests.get();
    }

    public long getTotalInitiatedRequests() {
        return totalInitiatedRequests.get();
    }

    public long getUpstreamCallsSaved() {
        return upstreamCallsSaved.get();
    }

    public int getActiveInFlightKeys() {
        return inFlight.size();
    }

    public void reset() {
        inFlight.clear();
        totalCoalescedRequests.set(0);
        totalInitiatedRequests.set(0);
        upstreamCallsSaved.set(0);
    }

    public Map<String, Object> getStats() {
        long coalesced = totalCoalescedRequests.get();
        long initiated = totalInitiatedRequests.get();
        long total = coalesced + initiated;
        double efficiencyRatio = total > 0 ? ((double) coalesced / total) * 100.0 : 0.0;

        return Map.of(
                "enabled", enabled,
                "timeoutMs", timeoutMs,
                "totalCoalescedRequests", coalesced,
                "totalInitiatedRequests", initiated,
                "upstreamCallsSaved", upstreamCallsSaved.get(),
                "activeInFlightKeys", inFlight.size(),
                "efficiencyRatioPercent", Math.round(efficiencyRatio * 10.0) / 10.0
        );
    }

    public static class InFlightGroup {
        private final CompletableFuture<CoalescedExecutionResult> future = new CompletableFuture<>();
        private final AtomicInteger waiterCount = new AtomicInteger(0);
        private final long startTime = System.currentTimeMillis();

        public CompletableFuture<CoalescedExecutionResult> getFuture() {
            return future;
        }

        public int getWaiterCount() {
            return waiterCount.get();
        }

        public long getStartTime() {
            return startTime;
        }
    }

    public static class CoalescedExecutionResult {
        private final ProxyResponse response;
        private final String originBackend;
        private final int failoverAttempts;
        private final String failoverOrigin;

        public CoalescedExecutionResult(ProxyResponse response, String originBackend,
                                       int failoverAttempts, String failoverOrigin) {
            this.response = response;
            this.originBackend = originBackend;
            this.failoverAttempts = failoverAttempts;
            this.failoverOrigin = failoverOrigin;
        }

        public ProxyResponse getResponse() {
            return response;
        }

        public String getOriginBackend() {
            return originBackend;
        }

        public int getFailoverAttempts() {
            return failoverAttempts;
        }

        public String getFailoverOrigin() {
            return failoverOrigin;
        }
    }

    public static class CoalescedResult {
        private final ProxyResponse response;
        private final boolean coalesced;
        private final int waiterCount;
        private final String originBackend;
        private final int failoverAttempts;
        private final String failoverOrigin;

        public CoalescedResult(ProxyResponse response, boolean coalesced, int waiterCount,
                               String originBackend, int failoverAttempts, String failoverOrigin) {
            this.response = response;
            this.coalesced = coalesced;
            this.waiterCount = waiterCount;
            this.originBackend = originBackend;
            this.failoverAttempts = failoverAttempts;
            this.failoverOrigin = failoverOrigin;
        }

        public ProxyResponse getResponse() {
            return response;
        }

        public boolean isCoalesced() {
            return coalesced;
        }

        public int getWaiterCount() {
            return waiterCount;
        }

        public String getOriginBackend() {
            return originBackend;
        }

        public int getFailoverAttempts() {
            return failoverAttempts;
        }

        public String getFailoverOrigin() {
            return failoverOrigin;
        }
    }
}
