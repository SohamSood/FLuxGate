package com.fluxgate.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class ModelRecordsTest {

    @Test
    @DisplayName("Should verify ErrorResponse record constructors and accessors")
    void testErrorResponse() {
        ErrorResponse err1 = new ErrorResponse(404, "Not Found", "Resource not found", "/api/items");
        assertEquals(404, err1.status());
        assertEquals(404, err1.getStatus());
        assertEquals("Not Found", err1.error());
        assertEquals("Not Found", err1.getError());
        assertEquals("Resource not found", err1.message());
        assertEquals("Resource not found", err1.getMessage());
        assertEquals("/api/items", err1.path());
        assertEquals("/api/items", err1.getPath());
        assertNull(err1.backend());
        assertNotNull(err1.timestamp());

        ErrorResponse err2 = new ErrorResponse(502, "Bad Gateway", "Backend unreachable", "/api/items", "http://backend:8081");
        assertEquals("http://backend:8081", err2.backend());
        assertEquals("http://backend:8081", err2.getBackend());
    }

    @Test
    @DisplayName("Should verify ProxyResponse getters and default values")
    void testProxyResponse() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Custom", "123");
        byte[] body = "response-content".getBytes(StandardCharsets.UTF_8);

        ProxyResponse resp = new ProxyResponse(200, headers, body, 45L);
        assertEquals(200, resp.getStatusCode());
        assertEquals("123", resp.getHeaders().getFirst("X-Custom"));
        assertArrayEquals(body, resp.getBody());
        assertEquals(45L, resp.getLatencyMs());

        // Null safety
        ProxyResponse nullSafe = new ProxyResponse(500, null, null, 10L);
        assertNotNull(nullSafe.getHeaders());
        assertNotNull(nullSafe.getBody());
    }

    @Test
    @DisplayName("Should verify RateLimitResult record accessors")
    void testRateLimitResult() {
        RateLimitResult allowed = new RateLimitResult(true, 59, 60, 0, "192.168.1.1");
        assertTrue(allowed.allowed());
        assertTrue(allowed.isAllowed());
        assertEquals(59, allowed.remainingTokens());
        assertEquals(59, allowed.getRemainingTokens());
        assertEquals(60, allowed.capacity());
        assertEquals(60, allowed.getCapacity());
        assertEquals(0, allowed.resetSeconds());
        assertEquals("192.168.1.1", allowed.clientIp());

        RateLimitResult rejected = new RateLimitResult(false, 0, 60, 15, "10.0.0.1");
        assertFalse(rejected.isAllowed());
        assertEquals(15, rejected.getResetSeconds());
    }

    @Test
    @DisplayName("Should verify ProxyPipelineResult record accessors")
    void testProxyPipelineResult() {
        ProxyResponse pr = new ProxyResponse(200, new HttpHeaders(), new byte[0], 12L);
        ProxyPipelineResult res = new ProxyPipelineResult(
                pr, "http://b1:8081", "b1", "MISS", false, 0, "CLOSED", 0, null
        );

        assertSame(pr, res.response());
        assertSame(pr, res.getResponse());
        assertEquals("http://b1:8081", res.originBackend());
        assertEquals("http://b1:8081", res.getOriginBackend());
        assertEquals("b1", res.getRouteLabel());
        assertEquals("MISS", res.getCacheStatus());
        assertFalse(res.isCoalesced());
        assertEquals(0, res.getWaiterCount());
        assertEquals("CLOSED", res.getCircuitBreakerState());
        assertEquals(0, res.getFailoverAttempts());
        assertNull(res.getFailoverOrigin());
    }

    @Test
    @DisplayName("Should verify RequestLogEntry getters")
    void testRequestLogEntry() {
        RequestLogEntry entry = new RequestLogEntry(
                1L, "127.0.0.1", "GET", "/api/test", "http://backend:8081",
                200, 15L, false, "MISS"
        );

        assertEquals(1L, entry.getId());
        assertEquals("127.0.0.1", entry.getClientIp());
        assertEquals("GET", entry.getMethod());
        assertEquals("/api/test", entry.getPath());
        assertEquals("http://backend:8081", entry.getTargetBackend());
        assertEquals(200, entry.getStatusCode());
        assertEquals(15L, entry.getLatencyMs());
        assertFalse(entry.isRateLimited());
        assertEquals("MISS", entry.getCacheStatus());
        assertNotNull(entry.getTimestamp());
    }
}
