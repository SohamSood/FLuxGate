package com.fluxgate.util;

import com.fluxgate.model.BackendServer;
import com.fluxgate.model.CachedResponse;
import com.fluxgate.model.ProxyResponse;
import com.fluxgate.model.RateLimitResult;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.net.http.HttpRequest;
import java.util.*;

/**
 * Utility methods for HTTP header parsing, client IP resolution,
 * request tracing ID generation, hop-by-hop header stripping (RFC 7230 §6.1),
 * security header injection, and proxy response decoration.
 */
public final class ProxyHeaderUtils {

    private ProxyHeaderUtils() {}

    /**
     * Hop-by-hop headers that must not be forwarded by an HTTP proxy (RFC 7230 §6.1).
     */
    public static final Set<String> HOP_BY_HOP_HEADERS = Set.of(
            "connection",
            "keep-alive",
            "proxy-authenticate",
            "proxy-authorization",
            "te",
            "trailer",
            "trailers",
            "transfer-encoding",
            "upgrade",
            "host",
            "content-length"
    );

    /**
     * Resolves the client IP from standard proxy headers (X-Forwarded-For, X-Real-IP)
     * with fallback to socket remote address.
     */
    public static String extractClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr() != null ? request.getRemoteAddr() : "127.0.0.1";
    }

    /**
     * Resolves or generates a unique correlation request ID for distributed tracing.
     */
    public static String resolveRequestId(HttpServletRequest request) {
        String id = request.getHeader("X-Request-Id");
        if (id == null || id.isBlank()) {
            id = request.getHeader("X-Correlation-Id");
        }
        if (id == null || id.isBlank()) {
            id = UUID.randomUUID().toString();
        }
        return id.trim();
    }

    /**
     * Detects whether the client explicitly requested cache bypass via Cache-Control or Pragma headers.
     */
    public static boolean isBypassCacheRequested(HttpServletRequest request) {
        String cacheControl = request.getHeader("Cache-Control");
        String pragma = request.getHeader("Pragma");
        return (cacheControl != null && (cacheControl.contains("no-cache") || cacheControl.contains("no-store")))
                || "no-cache".equalsIgnoreCase(pragma);
    }

    /**
     * Constructs the full upstream target URL.
     */
    public static String buildTargetUrl(String backendUrl, String requestUri, String queryString) {
        if (queryString != null && !queryString.isBlank()) {
            return backendUrl + requestUri + "?" + queryString;
        }
        return backendUrl + requestUri;
    }

    /**
     * Generates a unique cache key based on HTTP method, path, and query parameters.
     */
    public static String buildCacheKey(String method, String uri, String queryString) {
        return method + ":" + uri + (queryString != null && !queryString.isBlank() ? "?" + queryString : "");
    }

    /**
     * Copies allowable incoming request headers to the upstream HttpRequest builder,
     * stripping hop-by-hop headers and adding standard X-Forwarded-* and X-Request-Id headers.
     */
    public static void copyRequestHeaders(HttpServletRequest request, HttpRequest.Builder builder, BackendServer backend) {
        copyRequestHeaders(request, builder, backend, resolveRequestId(request));
    }

    public static void copyRequestHeaders(HttpServletRequest request, HttpRequest.Builder builder, BackendServer backend, String requestId) {
        Enumeration<String> headerNames = request.getHeaderNames();
        if (headerNames != null) {
            while (headerNames.hasMoreElements()) {
                String headerName = headerNames.nextElement();
                if (headerName == null) continue;

                if (HOP_BY_HOP_HEADERS.contains(headerName.toLowerCase())) {
                    continue;
                }

                Enumeration<String> values = request.getHeaders(headerName);
                if (values != null) {
                    while (values.hasMoreElements()) {
                        String value = values.nextElement();
                        if (value != null) {
                            try {
                                builder.header(headerName, value);
                            } catch (IllegalArgumentException ignored) {
                                // Ignore restricted or malformed headers
                            }
                        }
                    }
                }
            }
        }

        // Add standard X-Forwarded-* headers
        String clientIp = request.getHeader("X-Forwarded-For");
        if (clientIp == null || clientIp.isBlank()) {
            clientIp = request.getRemoteAddr();
        } else {
            clientIp = clientIp + ", " + request.getRemoteAddr();
        }
        builder.header("X-Forwarded-For", clientIp);
        builder.header("X-Forwarded-Proto", request.getScheme());
        builder.header("X-Forwarded-Host", request.getHeader("Host") != null ? request.getHeader("Host") : request.getServerName());
        builder.header("X-Forwarded-Port", String.valueOf(request.getServerPort()));
        if (requestId != null && !requestId.isBlank()) {
            builder.header("X-Request-Id", requestId);
        }
    }

    /**
     * Extracts response headers from upstream response map, stripping hop-by-hop headers.
     */
    public static HttpHeaders extractResponseHeaders(Map<String, List<String>> upstreamHeaders) {
        HttpHeaders headers = new HttpHeaders();
        for (Map.Entry<String, List<String>> entry : upstreamHeaders.entrySet()) {
            String name = entry.getKey();
            if (name == null) continue;

            if (HOP_BY_HOP_HEADERS.contains(name.toLowerCase())) {
                continue;
            }

            for (String val : entry.getValue()) {
                headers.add(name, val);
            }
        }
        return headers;
    }

    /**
     * Injects standard enterprise gateway security headers.
     */
    public static void applySecurityHeaders(HttpHeaders headers, String requestId) {
        if (requestId != null && !requestId.isBlank()) {
            headers.set("X-Request-Id", requestId);
        }
        headers.set("X-Content-Type-Options", "nosniff");
        headers.set("X-Frame-Options", "SAMEORIGIN");
    }

    /**
     * Builds HTTP headers for a 403 Forbidden response (IP Access Control firewall).
     */
    public static HttpHeaders createForbiddenHeaders(String requestId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Proxy-By", "FluxGate-Firewall");
        applySecurityHeaders(headers, requestId);
        return headers;
    }

    /**
     * Builds HTTP headers for a 429 Too Many Requests response.
     */
    public static HttpHeaders createRateLimitHeaders(RateLimitResult rateLimitResult) {
        return createRateLimitHeaders(rateLimitResult, null);
    }

    public static HttpHeaders createRateLimitHeaders(RateLimitResult rateLimitResult, String requestId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Retry-After", String.valueOf(rateLimitResult.getResetSeconds()));
        headers.set("X-RateLimit-Limit", String.valueOf(rateLimitResult.getCapacity()));
        headers.set("X-RateLimit-Remaining", "0");
        headers.set("X-RateLimit-Reset", String.valueOf(rateLimitResult.getResetSeconds()));
        headers.set("X-Proxy-By", "FluxGate-RateLimiter");
        applySecurityHeaders(headers, requestId);
        return headers;
    }

    /**
     * Builds HTTP headers for a Cache HIT response.
     */
    public static HttpHeaders createCacheHitHeaders(CachedResponse cached, RateLimitResult rateLimitResult) {
        return createCacheHitHeaders(cached, rateLimitResult, null);
    }

    public static HttpHeaders createCacheHitHeaders(CachedResponse cached, RateLimitResult rateLimitResult, String requestId) {
        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.addAll(cached.getHeaders());
        responseHeaders.set("X-Cache", "HIT");
        responseHeaders.set("Age", String.valueOf(cached.getAgeSeconds()));
        responseHeaders.set("X-Cache-TTL", String.valueOf(cached.getRemainingTtlSeconds()));
        responseHeaders.set("X-Proxy-Latency", "0ms (Edge Cache)");
        responseHeaders.set("X-Upstream-Backend", cached.getOriginBackend());
        responseHeaders.set("X-RateLimit-Limit", String.valueOf(rateLimitResult.getCapacity()));
        responseHeaders.set("X-RateLimit-Remaining", String.valueOf(rateLimitResult.getRemainingTokens()));
        responseHeaders.set("X-Circuit-Breaker", "BYPASS (Edge Cache)");
        responseHeaders.set("X-Failover-Attempts", "0");
        applySecurityHeaders(responseHeaders, requestId);
        return responseHeaders;
    }

    /**
     * Enriches upstream proxy response headers with telemetry, cache status, and routing metadata.
     */
    public static HttpHeaders createProxiedResponseHeaders(
            ProxyResponse proxyResponse,
            String cacheStatus,
            boolean wasCoalesced,
            int waiterCount,
            RateLimitResult rateLimitResult,
            String circuitBreakerState,
            int failoverAttempts,
            String failoverOrigin,
            String originBackend) {
        return createProxiedResponseHeaders(
                proxyResponse, cacheStatus, wasCoalesced, waiterCount,
                rateLimitResult, circuitBreakerState, failoverAttempts,
                failoverOrigin, originBackend, null
        );
    }

    public static HttpHeaders createProxiedResponseHeaders(
            ProxyResponse proxyResponse,
            String cacheStatus,
            boolean wasCoalesced,
            int waiterCount,
            RateLimitResult rateLimitResult,
            String circuitBreakerState,
            int failoverAttempts,
            String failoverOrigin,
            String originBackend,
            String requestId) {

        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.addAll(proxyResponse.getHeaders());
        responseHeaders.set("X-Cache", cacheStatus);
        responseHeaders.set("X-Coalesced", String.valueOf(wasCoalesced));
        responseHeaders.set("X-Coalesced-Waiters", String.valueOf(waiterCount));
        responseHeaders.set("X-RateLimit-Limit", String.valueOf(rateLimitResult.getCapacity()));
        responseHeaders.set("X-RateLimit-Remaining", String.valueOf(rateLimitResult.getRemainingTokens()));
        responseHeaders.set("X-Circuit-Breaker", circuitBreakerState);
        responseHeaders.set("X-Failover-Attempts", String.valueOf(failoverAttempts));
        if (failoverOrigin != null) {
            responseHeaders.set("X-Failover-Origin", failoverOrigin);
            responseHeaders.set("X-Failover-Target", originBackend);
        }
        applySecurityHeaders(responseHeaders, requestId);
        return responseHeaders;
    }
}
