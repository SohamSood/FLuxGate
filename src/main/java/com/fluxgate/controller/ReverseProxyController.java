package com.fluxgate.controller;

import com.fluxgate.model.CachedResponse;
import com.fluxgate.model.ProxyPipelineResult;
import com.fluxgate.model.RateLimitResult;
import com.fluxgate.service.IpAccessControlService;
import com.fluxgate.service.ProxyExecutionPipeline;
import com.fluxgate.service.RateLimiterService;
import com.fluxgate.service.ResponseCacheService;
import com.fluxgate.service.TrafficMetricsService;
import com.fluxgate.util.ProxyHeaderUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * Catch-all reverse proxy controller: coordinates incoming requests across IP firewall filtering,
 * rate limiting, edge response cache, and the modular proxy execution pipeline.
 */
@RestController
public class ReverseProxyController {

    private final IpAccessControlService ipAccessControlService;
    private final RateLimiterService rateLimiterService;
    private final ResponseCacheService responseCacheService;
    private final TrafficMetricsService trafficMetricsService;
    private final ProxyExecutionPipeline pipeline;

    public ReverseProxyController(IpAccessControlService ipAccessControlService,
                                  RateLimiterService rateLimiterService,
                                  ResponseCacheService responseCacheService,
                                  TrafficMetricsService trafficMetricsService,
                                  ProxyExecutionPipeline pipeline) {
        this.ipAccessControlService = ipAccessControlService;
        this.rateLimiterService = rateLimiterService;
        this.responseCacheService = responseCacheService;
        this.trafficMetricsService = trafficMetricsService;
        this.pipeline = pipeline;
    }

    @RequestMapping(value = "/**")
    public ResponseEntity<byte[]> handleRequest(
            HttpServletRequest request,
            @RequestBody(required = false) byte[] body) {

        String uri = request.getRequestURI();
        if ("/".equals(uri) || "/dashboard".equals(uri)) {
            return ResponseEntity.ok()
                    .header("Content-Type", "text/html; charset=UTF-8")
                    .body(DashboardController.DASHBOARD_HTML.getBytes(StandardCharsets.UTF_8));
        }

        String clientIp = ProxyHeaderUtils.extractClientIp(request);
        String requestId = ProxyHeaderUtils.resolveRequestId(request);

        // 1. IP Access Control Firewall
        if (!ipAccessControlService.isAllowed(clientIp)) {
            return handleIpBlocked(request, uri, clientIp, requestId);
        }

        // 2. Rate Limiting Check
        RateLimitResult rateLimitResult = rateLimiterService.tryConsume(clientIp);
        if (!rateLimitResult.isAllowed()) {
            return handleRateLimitExceeded(request, uri, clientIp, rateLimitResult, requestId);
        }

        String method = request.getMethod();
        String queryString = request.getQueryString();
        String cacheKey = ProxyHeaderUtils.buildCacheKey(method, uri, queryString);
        boolean isCacheableMethod = "GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method);
        boolean cacheBypassRequested = ProxyHeaderUtils.isBypassCacheRequested(request);

        // 3. Edge Cache Lookup for idempotent read requests
        if (isCacheableMethod && !cacheBypassRequested && responseCacheService.isEnabled()) {
            Optional<CachedResponse> cachedOpt = responseCacheService.get(cacheKey);
            if (cachedOpt.isPresent()) {
                return handleCacheHit(cachedOpt.get(), clientIp, method, uri, rateLimitResult, requestId);
            }
        }

        // 4. Dispatch through modular execution pipeline (coalescing, circuit breakers, failover, tracing)
        ProxyPipelineResult result = pipeline.dispatch(request, body, cacheKey, isCacheableMethod, cacheBypassRequested, requestId);

        // 5. Record traffic metrics
        trafficMetricsService.record(
                clientIp,
                method,
                uri,
                result.getRouteLabel(),
                result.getResponse().getStatusCode(),
                result.getResponse().getLatencyMs(),
                false,
                result.getCacheStatus()
        );

        // 6. Enrich and return response
        HttpHeaders responseHeaders = ProxyHeaderUtils.createProxiedResponseHeaders(
                result.getResponse(),
                result.getCacheStatus(),
                result.isCoalesced(),
                result.getWaiterCount(),
                rateLimitResult,
                result.getCircuitBreakerState(),
                result.getFailoverAttempts(),
                result.getFailoverOrigin(),
                result.getOriginBackend(),
                requestId
        );

        return ResponseEntity
                .status(result.getResponse().getStatusCode())
                .headers(responseHeaders)
                .body(result.getResponse().getBody());
    }

    private ResponseEntity<byte[]> handleIpBlocked(
            HttpServletRequest request, String uri, String clientIp, String requestId) {

        trafficMetricsService.record(
                clientIp,
                request.getMethod(),
                uri,
                "BLOCKED_BY_FIREWALL",
                HttpStatus.FORBIDDEN.value(),
                0,
                true,
                "BLOCKED"
        );

        HttpHeaders headers = ProxyHeaderUtils.createForbiddenHeaders(requestId);
        String errorJson = String.format(
                "{\"error\":\"Forbidden\",\"message\":\"Access denied by gateway firewall policy.\",\"clientIp\":\"%s\",\"requestId\":\"%s\"}",
                clientIp, requestId
        );

        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .headers(headers)
                .body(errorJson.getBytes(StandardCharsets.UTF_8));
    }

    private ResponseEntity<byte[]> handleRateLimitExceeded(
            HttpServletRequest request, String uri, String clientIp, RateLimitResult rateLimitResult, String requestId) {

        trafficMetricsService.record(
                clientIp,
                request.getMethod(),
                uri,
                "RATE_LIMITED",
                HttpStatus.TOO_MANY_REQUESTS.value(),
                0,
                true,
                "THROTTLED"
        );

        HttpHeaders headers = ProxyHeaderUtils.createRateLimitHeaders(rateLimitResult, requestId);
        String errorJson = String.format(
                "{\"error\":\"Too Many Requests\",\"message\":\"Rate limit exceeded. Please throttle requests.\",\"clientIp\":\"%s\",\"limit\":%d,\"retryAfterSeconds\":%d,\"requestId\":\"%s\"}",
                clientIp, rateLimitResult.getCapacity(), rateLimitResult.getResetSeconds(), requestId
        );

        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .headers(headers)
                .body(errorJson.getBytes(StandardCharsets.UTF_8));
    }

    private ResponseEntity<byte[]> handleCacheHit(
            CachedResponse cached, String clientIp, String method, String uri, RateLimitResult rateLimitResult, String requestId) {

        trafficMetricsService.record(
                clientIp,
                method,
                uri,
                cached.getOriginBackend() + " (Edge Cache)",
                cached.getStatusCode(),
                0,
                false,
                "HIT"
        );

        HttpHeaders responseHeaders = ProxyHeaderUtils.createCacheHitHeaders(cached, rateLimitResult, requestId);
        return ResponseEntity
                .status(cached.getStatusCode())
                .headers(responseHeaders)
                .body(cached.getBody());
    }
}
