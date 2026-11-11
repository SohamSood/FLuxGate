package com.fluxgate.controller;

import com.fluxgate.model.BackendServer;
import com.fluxgate.model.ProxyResponse;
import com.fluxgate.model.RateLimitResult;
import com.fluxgate.service.BackendRegistry;
import com.fluxgate.service.NoHealthyBackendException;
import com.fluxgate.service.ProxyService;
import com.fluxgate.service.RateLimiterService;
import com.fluxgate.service.TrafficMetricsService;
import com.fluxgate.strategy.LoadBalancingStrategy;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

/**
 * Catch-all controller intercepting client requests, enforcing rate limits,
 * selecting a healthy backend, and forwarding the request.
 */
@RestController
public class ReverseProxyController {

    private final BackendRegistry backendRegistry;
    private final LoadBalancingStrategy loadBalancingStrategy;
    private final ProxyService proxyService;
    private final RateLimiterService rateLimiterService;
    private final TrafficMetricsService trafficMetricsService;

    public ReverseProxyController(BackendRegistry backendRegistry,
                                  LoadBalancingStrategy loadBalancingStrategy,
                                  ProxyService proxyService,
                                  RateLimiterService rateLimiterService,
                                  TrafficMetricsService trafficMetricsService) {
        this.backendRegistry = backendRegistry;
        this.loadBalancingStrategy = loadBalancingStrategy;
        this.proxyService = proxyService;
        this.rateLimiterService = rateLimiterService;
        this.trafficMetricsService = trafficMetricsService;
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
                    true
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

        BackendServer backend = loadBalancingStrategy.select(backendRegistry.getAllBackends())
                .orElseThrow(() -> new NoHealthyBackendException(
                        "No healthy backend instances available to handle the request: " + request.getRequestURI()));

        ProxyResponse proxyResponse = proxyService.forward(request, body, backend);

        trafficMetricsService.record(
                clientIp,
                request.getMethod(),
                uri,
                backend.getUrl(),
                proxyResponse.getStatusCode(),
                proxyResponse.getLatencyMs(),
                false
        );

        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.addAll(proxyResponse.getHeaders());
        responseHeaders.set("X-RateLimit-Limit", String.valueOf(rateLimitResult.getCapacity()));
        responseHeaders.set("X-RateLimit-Remaining", String.valueOf(rateLimitResult.getRemainingTokens()));

        return ResponseEntity
                .status(proxyResponse.getStatusCode())
                .headers(responseHeaders)
                .body(proxyResponse.getBody());
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
