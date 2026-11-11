package com.fluxgate.service;

import com.fluxgate.model.RequestLogEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TrafficMetricsServiceTest {

    private TrafficMetricsService metricsService;

    @BeforeEach
    void setUp() {
        metricsService = new TrafficMetricsService();
    }

    @Test
    void testRecordAndAggregatedMetrics() {
        metricsService.record("127.0.0.1", "GET", "/hello", "http://backend-1", 200, 10, false);
        metricsService.record("127.0.0.1", "POST", "/api/order", "http://backend-2", 201, 20, false);
        metricsService.record("192.168.1.1", "GET", "/bad", "http://backend-1", 404, 5, false);
        metricsService.record("10.0.0.5", "GET", "/flood", "RATE_LIMITED", 429, 0, true);
        metricsService.record("127.0.0.1", "GET", "/crash", "http://backend-3", 500, 15, false);

        assertEquals(5, metricsService.getTotalRequests());
        assertEquals(2, metricsService.getTotal2xx());
        assertEquals(2, metricsService.getTotal4xx()); // 404 + 429
        assertEquals(1, metricsService.getTotal5xx());
        assertEquals(10.0, metricsService.getAverageLatencyMs()); // (10+20+5+0+15)/5 = 10.0

        List<RequestLogEntry> recent = metricsService.getRecentRequests();
        assertEquals(5, recent.size());
        assertEquals("/crash", recent.get(0).getPath()); // newest first
        assertTrue(recent.get(1).isRateLimited());
    }

    @Test
    void testResetMetrics() {
        metricsService.record("127.0.0.1", "GET", "/hello", "http://backend-1", 200, 12, false);
        assertEquals(1, metricsService.getTotalRequests());

        metricsService.reset();
        assertEquals(0, metricsService.getTotalRequests());
        assertEquals(0.0, metricsService.getAverageLatencyMs());
        assertTrue(metricsService.getRecentRequests().isEmpty());
    }
}
