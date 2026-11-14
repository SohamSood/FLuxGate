package com.fluxgate.controller;

import com.fluxgate.model.BackendServer;
import com.fluxgate.model.CachedResponse;
import com.fluxgate.model.ProxyResponse;
import com.fluxgate.model.RateLimitResult;
import com.fluxgate.resilience.CircuitBreakerManager;
import com.fluxgate.service.BadGatewayException;
import com.fluxgate.service.BackendRegistry;
import com.fluxgate.service.GatewayTimeoutException;
import com.fluxgate.service.NoHealthyBackendException;
import com.fluxgate.service.ProxyService;
import com.fluxgate.service.RateLimiterService;
import com.fluxgate.service.ResponseCacheService;
import com.fluxgate.service.TrafficMetricsService;
import com.fluxgate.strategy.LoadBalancingStrategy;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

/**
 * Catch-all reverse proxy controller: enforces rate limits, manages edge response caching,
 * routes traffic via load balancing strategies, manages circuit breakers, and executes
 * automatic in-flight failover protection.
 */
@RestController
public class ReverseProxyController {

    private static final Logger log = LoggerFactory.getLogger(ReverseProxyController.class);

    private final BackendRegistry backendRegistry;
    private final LoadBalancingStrategy loadBalancingStrategy;
    private final ProxyService proxyService;
    private final RateLimiterService rateLimiterService;
    private final ResponseCacheService responseCacheService;
    private final TrafficMetricsService trafficMetricsService;
    private final CircuitBreakerManager circuitBreakerManager;

    public ReverseProxyController(BackendRegistry backendRegistry,
                                  LoadBalancingStrategy loadBalancingStrategy,
                                  ProxyService proxyService,
                                  RateLimiterService rateLimiterService,
                                  ResponseCacheService responseCacheService,
                                  TrafficMetricsService trafficMetricsService,
                                  CircuitBreakerManager circuitBreakerManager) {
        this.backendRegistry = backendRegistry;
        this.loadBalancingStrategy = loadBalancingStrategy;
        this.proxyService = proxyService;
        this.rateLimiterService = rateLimiterService;
        this.responseCacheService = responseCacheService;
        this.trafficMetricsService = trafficMetricsService;
        this.circuitBreakerManager = circuitBreakerManager;
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

        String clientIp = extractClientIp(request);
        RateLimitResult rateLimitResult = rateLimiterService.tryConsume(clientIp);

        if (!rateLimitResult.isAllowed()) {
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

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Retry-After", String.valueOf(rateLimitResult.getResetSeconds()));
            headers.set("X-RateLimit-Limit", String.valueOf(rateLimitResult.getCapacity()));
            headers.set("X-RateLimit-Remaining", "0");
            headers.set("X-RateLimit-Reset", String.valueOf(rateLimitResult.getResetSeconds()));
            headers.set("X-Proxy-By", "FluxGate-RateLimiter");

            String errorJson = String.format(
                    "{\"error\":\"Too Many Requests\",\"message\":\"Rate limit exceeded. Please throttle requests.\",\"clientIp\":\"%s\",\"limit\":%d,\"retryAfterSeconds\":%d}",
                    clientIp, rateLimitResult.getCapacity(), rateLimitResult.getResetSeconds()
            );

            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .headers(headers)
                    .body(errorJson.getBytes(StandardCharsets.UTF_8));
        }

        String method = request.getMethod();
        String queryString = request.getQueryString();
        String cacheKey = method + ":" + uri + (queryString != null && !queryString.isBlank() ? "?" + queryString : "");
        boolean isCacheableMethod = "GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method);
        boolean cacheBypassRequested = isBypassCacheRequested(request);

        // 1. Edge Cache Lookup for idempotent read requests
        if (isCacheableMethod && !cacheBypassRequested && responseCacheService.isEnabled()) {
            Optional<CachedResponse> cachedOpt = responseCacheService.get(cacheKey);
            if (cachedOpt.isPresent()) {
                CachedResponse cached = cachedOpt.get();
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

                return ResponseEntity
                        .status(cached.getStatusCode())
                        .headers(responseHeaders)
                        .body(cached.getBody());
            }
        }

        // 2. Select initial backend with Circuit Breaker filter
        List<BackendServer> permittedBackends = backendRegistry.getHealthyBackends().stream()
                .filter(b -> circuitBreakerManager.isRequestPermitted(b.getUrl()))
                .toList();

        List<BackendServer> selectionPool = !permittedBackends.isEmpty()
                ? permittedBackends
                : backendRegistry.getAllBackends();

        BackendServer backend = loadBalancingStrategy.select(selectionPool)
                .orElseThrow(() -> new NoHealthyBackendException(
                        "No healthy backend instances available to handle the request: " + request.getRequestURI()));

        // Check if circuit breaker allows request to primary backend
        if (!circuitBreakerManager.allowRequest(backend.getUrl())) {
            Optional<BackendServer> alternateOpt = selectFailoverBackend(backend);
            if (alternateOpt.isPresent()) {
                backend = alternateOpt.get();
                circuitBreakerManager.allowRequest(backend.getUrl());
            }
        }

        // 3. Forward request with automatic in-flight failover protection
        ProxyResponse proxyResponse;
        BackendServer activeBackend = backend;
        int failoverAttempts = 0;
        String failoverOrigin = null;

        try {
            proxyResponse = proxyService.forward(request, body, activeBackend);

            // If upstream returns 5xx error, record failure in circuit breaker and attempt transparent in-flight failover
            if (proxyResponse.getStatusCode() >= 500) {
                String failureReason = "HTTP " + proxyResponse.getStatusCode() + " upstream error";
                circuitBreakerManager.recordFailure(activeBackend.getUrl(), failureReason);

                Optional<BackendServer> failoverOpt = selectFailoverBackend(activeBackend);
                if (failoverOpt.isPresent()) {
                    BackendServer alternate = failoverOpt.get();
                    failoverOrigin = activeBackend.getUrl();
                    activeBackend = alternate;
                    failoverAttempts++;
                    circuitBreakerManager.recordFailover(failoverOrigin);
                    log.warn("[IN-FLIGHT FAILOVER] Backend {} returned 5xx. Transparently rerouting request to alternate backend {}",
                            failoverOrigin, activeBackend.getUrl());
                    proxyResponse = proxyService.forward(request, body, activeBackend);
                }
            }
        } catch (BadGatewayException | GatewayTimeoutException ex) {
            String failureReason = ex.getMessage();
            circuitBreakerManager.recordFailure(activeBackend.getUrl(), failureReason);

            Optional<BackendServer> failoverOpt = selectFailoverBackend(activeBackend);
            if (failoverOpt.isPresent()) {
                BackendServer alternate = failoverOpt.get();
                failoverOrigin = activeBackend.getUrl();
                activeBackend = alternate;
                failoverAttempts++;
                circuitBreakerManager.recordFailover(failoverOrigin);
                log.warn("[IN-FLIGHT FAILOVER] Backend {} failed ({}). Transparently rerouting request to alternate backend {}",
                        failoverOrigin, failureReason, activeBackend.getUrl());
                proxyResponse = proxyService.forward(request, body, activeBackend);
            } else {
                throw ex;
            }
        }

        if (proxyResponse.getStatusCode() < 500) {
            circuitBreakerManager.recordSuccess(activeBackend.getUrl());
        }

        // 4. Determine cache status & handle store or invalidation
        String cacheStatus;
        if (isCacheableMethod) {
            if (cacheBypassRequested || !responseCacheService.isEnabled()) {
                cacheStatus = "BYPASS";
            } else {
                cacheStatus = "MISS";
                if (proxyResponse.getStatusCode() >= 200 && proxyResponse.getStatusCode() < 300) {
                    responseCacheService.put(
                            cacheKey,
                            proxyResponse.getStatusCode(),
                            proxyResponse.getHeaders(),
                            proxyResponse.getBody(),
                            activeBackend.getUrl()
                    );
                }
            }
        } else {
            // State-mutating request (POST, PUT, DELETE, PATCH): Invalidate affected cache keys
            responseCacheService.invalidatePath(uri);
            cacheStatus = "BYPASS";
        }

        String routeLabel = activeBackend.getUrl();
        if (failoverAttempts > 0) {
            routeLabel += " (Failover from " + failoverOrigin + ")";
        }

        trafficMetricsService.record(
                clientIp,
                method,
                uri,
                routeLabel,
                proxyResponse.getStatusCode(),
                proxyResponse.getLatencyMs(),
                false,
                cacheStatus
        );

        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.addAll(proxyResponse.getHeaders());
        responseHeaders.set("X-Cache", cacheStatus);
        responseHeaders.set("X-RateLimit-Limit", String.valueOf(rateLimitResult.getCapacity()));
        responseHeaders.set("X-RateLimit-Remaining", String.valueOf(rateLimitResult.getRemainingTokens()));
        responseHeaders.set("X-Circuit-Breaker", circuitBreakerManager.getCircuitBreaker(activeBackend.getUrl()).getState().name());
        responseHeaders.set("X-Failover-Attempts", String.valueOf(failoverAttempts));
        if (failoverOrigin != null) {
            responseHeaders.set("X-Failover-Origin", failoverOrigin);
            responseHeaders.set("X-Failover-Target", activeBackend.getUrl());
        }

        return ResponseEntity
                .status(proxyResponse.getStatusCode())
                .headers(responseHeaders)
                .body(proxyResponse.getBody());
    }

    private Optional<BackendServer> selectFailoverBackend(BackendServer failedBackend) {
        List<BackendServer> candidates = backendRegistry.getHealthyBackends().stream()
                .filter(b -> !b.getUrl().equalsIgnoreCase(failedBackend.getUrl()))
                .filter(b -> circuitBreakerManager.isRequestPermitted(b.getUrl()))
                .toList();
        if (!candidates.isEmpty()) {
            return loadBalancingStrategy.select(candidates);
        }
        return Optional.empty();
    }

    private boolean isBypassCacheRequested(HttpServletRequest request) {
        String cacheControl = request.getHeader("Cache-Control");
        String pragma = request.getHeader("Pragma");
        return (cacheControl != null && (cacheControl.contains("no-cache") || cacheControl.contains("no-store")))
                || "no-cache".equalsIgnoreCase(pragma);
    }

    private String extractClientIp(HttpServletRequest request) {
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
}
