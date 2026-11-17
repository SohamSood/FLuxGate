package com.fluxgate.util;

import com.fluxgate.model.BackendServer;
import com.fluxgate.model.CachedResponse;
import com.fluxgate.model.ProxyResponse;
import com.fluxgate.model.RateLimitResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ProxyHeaderUtilsTest {

    @Test
    @DisplayName("Should extract client IP from X-Forwarded-For first entry")
    void testExtractClientIpFromXForwardedFor() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "203.0.113.195, 70.41.3.18, 150.172.238.178");
        request.setRemoteAddr("127.0.0.1");

        String ip = ProxyHeaderUtils.extractClientIp(request);
        assertEquals("203.0.113.195", ip);
    }

    @Test
    @DisplayName("Should extract client IP from X-Real-IP if X-Forwarded-For is absent")
    void testExtractClientIpFromXRealIp() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Real-IP", "198.51.100.1");
        request.setRemoteAddr("127.0.0.1");

        String ip = ProxyHeaderUtils.extractClientIp(request);
        assertEquals("198.51.100.1", ip);
    }

    @Test
    @DisplayName("Should fall back to remoteAddr if no forwarding headers present")
    void testExtractClientIpFallback() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.168.1.50");

        String ip = ProxyHeaderUtils.extractClientIp(request);
        assertEquals("192.168.1.50", ip);
    }

    @Test
    @DisplayName("Should resolve existing X-Request-Id or generate new UUID")
    void testResolveRequestId() {
        MockHttpServletRequest reqWithId = new MockHttpServletRequest();
        reqWithId.addHeader("X-Request-Id", "custom-req-12345");
        assertEquals("custom-req-12345", ProxyHeaderUtils.resolveRequestId(reqWithId));

        MockHttpServletRequest reqWithCorr = new MockHttpServletRequest();
        reqWithCorr.addHeader("X-Correlation-Id", "corr-999");
        assertEquals("corr-999", ProxyHeaderUtils.resolveRequestId(reqWithCorr));

        MockHttpServletRequest emptyReq = new MockHttpServletRequest();
        String generated = ProxyHeaderUtils.resolveRequestId(emptyReq);
        assertNotNull(generated);
        assertFalse(generated.isBlank());
    }

    @Test
    @DisplayName("Should detect cache bypass headers correctly")
    void testIsBypassCacheRequested() {
        MockHttpServletRequest req1 = new MockHttpServletRequest();
        req1.addHeader("Cache-Control", "no-cache");
        assertTrue(ProxyHeaderUtils.isBypassCacheRequested(req1));

        MockHttpServletRequest req2 = new MockHttpServletRequest();
        req2.addHeader("Pragma", "no-cache");
        assertTrue(ProxyHeaderUtils.isBypassCacheRequested(req2));

        MockHttpServletRequest req3 = new MockHttpServletRequest();
        req3.addHeader("Cache-Control", "max-age=3600");
        assertFalse(ProxyHeaderUtils.isBypassCacheRequested(req3));
    }

    @Test
    @DisplayName("Should format target URLs and cache keys cleanly")
    void testUrlAndCacheKeyConstruction() {
        String target1 = ProxyHeaderUtils.buildTargetUrl("http://backend:8080", "/api/test", "q=search");
        assertEquals("http://backend:8080/api/test?q=search", target1);

        String target2 = ProxyHeaderUtils.buildTargetUrl("http://backend:8080", "/api/test", null);
        assertEquals("http://backend:8080/api/test", target2);

        String key = ProxyHeaderUtils.buildCacheKey("GET", "/api/test", "q=search");
        assertEquals("GET:/api/test?q=search", key);
    }

    @Test
    @DisplayName("Should filter hop-by-hop headers when extracting upstream response headers")
    void testExtractResponseHeadersFiltersHopByHop() {
        Map<String, List<String>> upstream = Map.of(
                "Content-Type", List.of("application/json"),
                "Transfer-Encoding", List.of("chunked"),
                "Connection", List.of("keep-alive"),
                "X-Custom-Result", List.of("Success")
        );

        HttpHeaders extracted = ProxyHeaderUtils.extractResponseHeaders(upstream);
        assertEquals("application/json", extracted.getFirst("Content-Type"));
        assertEquals("Success", extracted.getFirst("X-Custom-Result"));
        assertNull(extracted.getFirst("Transfer-Encoding"));
        assertNull(extracted.getFirst("Connection"));
    }

    @Test
    @DisplayName("Should create rate limit error headers with proper metadata and security headers")
    void testCreateRateLimitHeaders() {
        RateLimitResult result = new RateLimitResult(false, 0, 100, 30, "10.0.0.1");
        HttpHeaders headers = ProxyHeaderUtils.createRateLimitHeaders(result, "req-123");

        assertEquals("30", headers.getFirst("Retry-After"));
        assertEquals("100", headers.getFirst("X-RateLimit-Limit"));
        assertEquals("0", headers.getFirst("X-RateLimit-Remaining"));
        assertEquals("FluxGate-RateLimiter", headers.getFirst("X-Proxy-By"));
        assertEquals("req-123", headers.getFirst("X-Request-Id"));
        assertEquals("nosniff", headers.getFirst("X-Content-Type-Options"));
    }

    @Test
    @DisplayName("Should create forbidden headers for IP firewall with request ID and security headers")
    void testCreateForbiddenHeaders() {
        HttpHeaders headers = ProxyHeaderUtils.createForbiddenHeaders("req-block-456");
        assertEquals("FluxGate-Firewall", headers.getFirst("X-Proxy-By"));
        assertEquals("req-block-456", headers.getFirst("X-Request-Id"));
        assertEquals("nosniff", headers.getFirst("X-Content-Type-Options"));
        assertEquals("SAMEORIGIN", headers.getFirst("X-Frame-Options"));
    }

    @Test
    @DisplayName("Should enrich proxied response headers with failover, circuit breaker telemetry, and tracing")
    void testCreateProxiedResponseHeaders() {
        ProxyResponse proxyResp = new ProxyResponse(200, new HttpHeaders(), new byte[0], 42);
        RateLimitResult rateLimitResult = new RateLimitResult(true, 59, 60, 0, "10.0.0.1");

        HttpHeaders headers = ProxyHeaderUtils.createProxiedResponseHeaders(
                proxyResp, "MISS", false, 0, rateLimitResult,
                "CLOSED", 1, "http://bad-backend:8081", "http://good-backend:8082", "req-xyz"
        );

        assertEquals("MISS", headers.getFirst("X-Cache"));
        assertEquals("false", headers.getFirst("X-Coalesced"));
        assertEquals("CLOSED", headers.getFirst("X-Circuit-Breaker"));
        assertEquals("1", headers.getFirst("X-Failover-Attempts"));
        assertEquals("http://bad-backend:8081", headers.getFirst("X-Failover-Origin"));
        assertEquals("http://good-backend:8082", headers.getFirst("X-Failover-Target"));
        assertEquals("req-xyz", headers.getFirst("X-Request-Id"));
        assertEquals("nosniff", headers.getFirst("X-Content-Type-Options"));
        assertEquals("SAMEORIGIN", headers.getFirst("X-Frame-Options"));
    }
}
