package com.fluxgate.service;

import com.fluxgate.model.RequestLogEntry;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;

/**
 * High-performance telemetry buffer and aggregated traffic metrics engine.
 * Records live transactions and provides real-time observability for FluxGate.
 */
@Service
public class TrafficMetricsService {

    private static final int MAX_BUFFER_SIZE = 60;

    private final AtomicLong nextLogId = new AtomicLong(1);
    private final ConcurrentLinkedDeque<RequestLogEntry> recentRequests = new ConcurrentLinkedDeque<>();

    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong total2xx = new AtomicLong(0);
    private final AtomicLong total4xx = new AtomicLong(0);
    private final AtomicLong total5xx = new AtomicLong(0);
    private final AtomicLong totalLatencyMs = new AtomicLong(0);

    /**
     * Records a completed transaction or throttled request.
     */
    public void record(String clientIp, String method, String path,
                       String targetBackend, int statusCode, long latencyMs, boolean rateLimited) {

        totalRequests.incrementAndGet();
        totalLatencyMs.addAndGet(Math.max(0, latencyMs));

        if (statusCode >= 200 && statusCode < 300) {
            total2xx.incrementAndGet();
        } else if (statusCode >= 400 && statusCode < 500) {
            total4xx.incrementAndGet();
        } else if (statusCode >= 500) {
            total5xx.incrementAndGet();
        }

        RequestLogEntry entry = new RequestLogEntry(
                nextLogId.getAndIncrement(),
                clientIp,
                method,
                path,
                targetBackend,
                statusCode,
                latencyMs,
                rateLimited
        );

        recentRequests.addFirst(entry);
        while (recentRequests.size() > MAX_BUFFER_SIZE) {
            recentRequests.pollLast();
        }
    }

    public List<RequestLogEntry> getRecentRequests() {
        return new ArrayList<>(recentRequests);
    }

    public long getTotalRequests() {
        return totalRequests.get();
    }

    public long getTotal2xx() {
        return total2xx.get();
    }

    public long getTotal4xx() {
        return total4xx.get();
    }

    public long getTotal5xx() {
        return total5xx.get();
    }

    public double getAverageLatencyMs() {
        long count = totalRequests.get();
        if (count == 0) {
            return 0.0;
        }
        return Math.round(((double) totalLatencyMs.get() / (double) count) * 10.0) / 10.0;
    }

    public void reset() {
        recentRequests.clear();
        totalRequests.set(0);
        total2xx.set(0);
        total4xx.set(0);
        total5xx.set(0);
        totalLatencyMs.set(0);
    }
}
