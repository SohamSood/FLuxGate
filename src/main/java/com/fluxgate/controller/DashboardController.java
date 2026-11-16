package com.fluxgate.controller;

import com.fluxgate.model.BackendServer;
import com.fluxgate.resilience.CircuitBreakerManager;
import com.fluxgate.service.BackendRegistry;
import com.fluxgate.service.RateLimiterService;
import com.fluxgate.service.RequestCoalescingService;
import com.fluxgate.service.ResponseCacheService;
import com.fluxgate.service.TrafficMetricsService;
import com.fluxgate.strategy.LoadBalancingStrategyManager;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Serves the interactive Web Dashboard and API landing page for FluxGate.
 */
@Controller
public class DashboardController {

    private final BackendRegistry backendRegistry;
    private final LoadBalancingStrategyManager strategyManager;
    private final RateLimiterService rateLimiterService;
    private final ResponseCacheService responseCacheService;
    private final TrafficMetricsService trafficMetricsService;
    private final CircuitBreakerManager circuitBreakerManager;
    private final RequestCoalescingService requestCoalescingService;

    public DashboardController(BackendRegistry backendRegistry,
                               LoadBalancingStrategyManager strategyManager,
                               RateLimiterService rateLimiterService,
                               ResponseCacheService responseCacheService,
                               TrafficMetricsService trafficMetricsService,
                               CircuitBreakerManager circuitBreakerManager,
                               RequestCoalescingService requestCoalescingService) {
        this.backendRegistry = backendRegistry;
        this.strategyManager = strategyManager;
        this.rateLimiterService = rateLimiterService;
        this.responseCacheService = responseCacheService;
        this.trafficMetricsService = trafficMetricsService;
        this.circuitBreakerManager = circuitBreakerManager;
        this.requestCoalescingService = requestCoalescingService;
    }

    @GetMapping(value = {"/", "/dashboard"}, produces = MediaType.TEXT_HTML_VALUE)
    @ResponseBody
    public String getDashboardHtml() {
        return DASHBOARD_HTML;
    }

    @GetMapping(value = "/", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getLandingJson() {
        List<BackendServer> all = backendRegistry.getAllBackends();
        List<BackendServer> healthy = backendRegistry.getHealthyBackends();

        Map<String, Object> response = new HashMap<>();
        response.put("service", "FluxGate Reverse Proxy & Load Balancer");
        response.put("version", "1.4.0");
        response.put("status", healthy.isEmpty() ? "DEGRADED" : "HEALTHY");
        response.put("strategy", strategyManager.getActiveStrategyName());
        response.put("healthyBackends", healthy.size());
        response.put("totalBackends", all.size());
        response.put("rateLimiterEnabled", rateLimiterService.isEnabled());
        response.put("edgeCacheEnabled", responseCacheService.isEnabled());
        response.put("coalescingEnabled", requestCoalescingService.isEnabled());
        response.put("coalescing", requestCoalescingService.getStats());
        response.put("circuitBreakers", circuitBreakerManager.getTelemetry());
        response.put("dashboardUrl", "/dashboard");
        response.put("testEndpoint", "/hello");
        response.put("managementEndpoints", Map.of(
                "health", "/fluxgate/health",
                "status", "/fluxgate/status",
                "circuitBreakers", "/fluxgate/circuit-breakers",
                "strategy", "/fluxgate/strategy",
                "rateLimit", "/fluxgate/rate-limit",
                "cache", "/fluxgate/cache",
                "coalescing", "/fluxgate/coalescing",
                "metrics", "/fluxgate/metrics",
                "backends", "/fluxgate/backends",
                "healthCheck", "/fluxgate/health-check"
        ));

        return ResponseEntity.ok(response);
    }

    public static final String DASHBOARD_HTML = DashboardTemplate.HTML;
}
