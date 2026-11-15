package com.fluxgate.controller;

import com.fluxgate.model.BackendServer;
import com.fluxgate.resilience.CircuitBreakerManager;
import com.fluxgate.service.BackendRegistry;
import com.fluxgate.service.HealthCheckService;
import com.fluxgate.service.RateLimiterService;
import com.fluxgate.service.RequestCoalescingService;
import com.fluxgate.service.ResponseCacheService;
import com.fluxgate.service.TrafficMetricsService;
import com.fluxgate.strategy.LoadBalancingStrategyManager;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Exposes administrative, rate-limiting, edge cache, circuit breaker, and telemetry monitoring endpoints for FluxGate.
 */
@RestController
@RequestMapping("/fluxgate")
public class FluxGateManagementController {

    private final BackendRegistry backendRegistry;
    private final HealthCheckService healthCheckService;
    private final LoadBalancingStrategyManager strategyManager;
    private final RateLimiterService rateLimiterService;
    private final ResponseCacheService responseCacheService;
    private final TrafficMetricsService trafficMetricsService;
    private final CircuitBreakerManager circuitBreakerManager;
    private final RequestCoalescingService requestCoalescingService;

    public FluxGateManagementController(BackendRegistry backendRegistry,
                                        HealthCheckService healthCheckService,
                                        LoadBalancingStrategyManager strategyManager,
                                        RateLimiterService rateLimiterService,
                                        ResponseCacheService responseCacheService,
                                        TrafficMetricsService trafficMetricsService,
                                        CircuitBreakerManager circuitBreakerManager,
                                        RequestCoalescingService requestCoalescingService) {
        this.backendRegistry = backendRegistry;
        this.healthCheckService = healthCheckService;
        this.strategyManager = strategyManager;
        this.rateLimiterService = rateLimiterService;
        this.responseCacheService = responseCacheService;
        this.trafficMetricsService = trafficMetricsService;
        this.circuitBreakerManager = circuitBreakerManager;
        this.requestCoalescingService = requestCoalescingService;
    }

    /**
     * Self-health check endpoint used by Render / container orchestrators.
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> getHealth() {
        List<BackendServer> all = backendRegistry.getAllBackends();
        List<BackendServer> healthy = backendRegistry.getHealthyBackends();

        Map<String, Object> response = new HashMap<>();
        response.put("status", "UP");
        response.put("service", "FluxGate");
        response.put("healthyBackends", healthy.size());
        response.put("totalBackends", all.size());
        response.put("strategy", strategyManager.getActiveStrategyName());
        response.put("rateLimiterEnabled", rateLimiterService.isEnabled());
        response.put("edgeCacheEnabled", responseCacheService.isEnabled());
        response.put("coalescingEnabled", requestCoalescingService.isEnabled());
        response.put("upstreamCallsSaved", requestCoalescingService.getUpstreamCallsSaved());
        response.put("circuitBreakersOpen", circuitBreakerManager.getTelemetry().get("openCircuits"));
        response.put("circuitBreakerEnabled", circuitBreakerManager.isEnabled());

        return ResponseEntity.ok(response);
    }

    /**
     * Returns full status information for all registered backend instances, rate limiter, edge cache, circuit breakers, and request coalescing.
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        List<BackendServer> all = backendRegistry.getAllBackends();
        List<BackendServer> healthy = backendRegistry.getHealthyBackends();

        Map<String, Object> response = new HashMap<>();
        response.put("service", "FluxGate Reverse Proxy & Load Balancer");
        response.put("strategy", strategyManager.getActiveStrategyName());
        response.put("availableStrategies", strategyManager.getAvailableStrategies());
        response.put("healthyCount", healthy.size());
        response.put("totalCount", all.size());
        response.put("backends", all);
        response.put("rateLimiter", Map.of(
                "enabled", rateLimiterService.isEnabled(),
                "capacity", rateLimiterService.getCapacity(),
                "refillRatePerMinute", rateLimiterService.getRefillRatePerMinute(),
                "blockedRequests", rateLimiterService.getTotalBlockedRequests()
        ));
        response.put("cache", responseCacheService.getStats());
        response.put("coalescing", requestCoalescingService.getStats());
        response.put("circuitBreakers", circuitBreakerManager.getTelemetry());
        response.put("metrics", Map.of(
                "totalRequests", trafficMetricsService.getTotalRequests(),
                "averageLatencyMs", trafficMetricsService.getAverageLatencyMs(),
                "status2xx", trafficMetricsService.getTotal2xx(),
                "status4xx", trafficMetricsService.getTotal4xx(),
                "status5xx", trafficMetricsService.getTotal5xx(),
                "cacheHits", trafficMetricsService.getTotalCacheHits(),
                "coalescedRequests", trafficMetricsService.getTotalCoalesced()
        ));

        return ResponseEntity.ok(response);
    }

    /**
     * Returns edge response cache statistics and telemetry.
     */
    @GetMapping("/cache")
    public ResponseEntity<Map<String, Object>> getCacheStats() {
        return ResponseEntity.ok(responseCacheService.getStats());
    }

    /**
     * Updates edge response cache configuration dynamically.
     */
    @PostMapping("/cache")
    public ResponseEntity<Map<String, Object>> updateCacheConfig(
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) Integer defaultTtlSeconds,
            @RequestParam(required = false) Integer maxEntries) {

        if (enabled != null) {
            responseCacheService.setEnabled(enabled);
        }
        if (defaultTtlSeconds != null) {
            responseCacheService.setDefaultTtlSeconds(defaultTtlSeconds);
        }
        if (maxEntries != null) {
            responseCacheService.setMaxEntries(maxEntries);
        }

        Map<String, Object> res = new HashMap<>(responseCacheService.getStats());
        res.put("message", "Edge cache configuration updated successfully");
        return ResponseEntity.ok(res);
    }

    /**
     * Purges all or targeted entries from edge response cache.
     */
    @DeleteMapping("/cache")
    public ResponseEntity<Map<String, Object>> purgeCache(
            @RequestParam(required = false) String key,
            @RequestParam(required = false) String path) {

        if (key != null && !key.isBlank()) {
            boolean removed = responseCacheService.invalidate(key);
            return ResponseEntity.ok(Map.of(
                    "message", removed ? "Key invalidated from cache" : "Key not found in cache",
                    "key", key,
                    "removed", removed
            ));
        } else if (path != null && !path.isBlank()) {
            int removed = responseCacheService.invalidatePath(path);
            return ResponseEntity.ok(Map.of(
                    "message", "Path invalidated from cache",
                    "path", path,
                    "removedCount", removed
            ));
        } else {
            int before = responseCacheService.getEntriesCount();
            responseCacheService.clear();
            return ResponseEntity.ok(Map.of(
                    "message", "Edge cache purged successfully",
                    "purgedEntries", before
            ));
        }
    }

    /**
     * Returns current and available load balancing strategies.
     */
    @GetMapping("/strategy")
    public ResponseEntity<Map<String, Object>> getStrategy() {
        return ResponseEntity.ok(Map.of(
                "active", strategyManager.getActiveStrategyName(),
                "available", strategyManager.getAvailableStrategies()
        ));
    }

    /**
     * Updates active load balancing strategy dynamically at runtime.
     */
    @PostMapping("/strategy")
    public ResponseEntity<Map<String, Object>> setStrategy(@RequestParam String name) {
        try {
            strategyManager.setActiveStrategy(name);
            return ResponseEntity.ok(Map.of(
                    "message", "Load balancing strategy updated successfully",
                    "active", strategyManager.getActiveStrategyName()
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Retrieves rate limiter configuration and telemetry.
     */
    @GetMapping("/rate-limit")
    public ResponseEntity<Map<String, Object>> getRateLimit() {
        return ResponseEntity.ok(Map.of(
                "enabled", rateLimiterService.isEnabled(),
                "capacity", rateLimiterService.getCapacity(),
                "refillRatePerMinute", rateLimiterService.getRefillRatePerMinute(),
                "totalAllowedRequests", rateLimiterService.getTotalAllowedRequests(),
                "totalBlockedRequests", rateLimiterService.getTotalBlockedRequests(),
                "trackedIpCount", rateLimiterService.getTrackedIpCount()
        ));
    }

    /**
     * Updates rate limiter configuration dynamically at runtime.
     */
    @PostMapping("/rate-limit")
    public ResponseEntity<Map<String, Object>> updateRateLimit(
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) Integer capacity,
            @RequestParam(required = false) Integer refillRatePerMinute) {

        if (enabled != null) {
            rateLimiterService.setEnabled(enabled);
        }
        if (capacity != null) {
            rateLimiterService.setCapacity(capacity);
        }
        if (refillRatePerMinute != null) {
            rateLimiterService.setRefillRatePerMinute(refillRatePerMinute);
        }

        return ResponseEntity.ok(Map.of(
                "message", "Rate limiter configuration updated successfully",
                "enabled", rateLimiterService.isEnabled(),
                "capacity", rateLimiterService.getCapacity(),
                "refillRatePerMinute", rateLimiterService.getRefillRatePerMinute()
        ));
    }

    /**
     * Retrieves live ingress traffic metrics and recent request transactions.
     */
    @GetMapping("/metrics")
    public ResponseEntity<Map<String, Object>> getMetrics() {
        return ResponseEntity.ok(Map.of(
                "totalRequests", trafficMetricsService.getTotalRequests(),
                "averageLatencyMs", trafficMetricsService.getAverageLatencyMs(),
                "status2xx", trafficMetricsService.getTotal2xx(),
                "status4xx", trafficMetricsService.getTotal4xx(),
                "status5xx", trafficMetricsService.getTotal5xx(),
                "totalBlockedRequests", rateLimiterService.getTotalBlockedRequests(),
                "cacheHits", trafficMetricsService.getTotalCacheHits(),
                "recentRequests", trafficMetricsService.getRecentRequests()
        ));
    }

    /**
     * Resets traffic metrics buffer and counters.
     */
    @DeleteMapping("/metrics")
    public ResponseEntity<Map<String, Object>> resetMetrics() {
        trafficMetricsService.reset();
        return ResponseEntity.ok(Map.of("message", "Traffic metrics and logs reset successfully"));
    }

    /**
     * Triggers an immediate health check across all registered backends.
     */
    @PostMapping("/health-check")
    public ResponseEntity<Map<String, Object>> triggerHealthCheck() {
        healthCheckService.runPeriodicHealthChecks();
        return ResponseEntity.ok(Map.of(
                "message", "Health check completed",
                "healthyCount", backendRegistry.getHealthyBackends().size(),
                "totalCount", backendRegistry.getAllBackends().size()
        ));
    }

    /**
     * Registers a new backend URL dynamically at runtime with optional weight.
     */
    @PostMapping("/backends")
    public ResponseEntity<Map<String, Object>> addBackend(@RequestBody Map<String, Object> body) {
        String url = body.get("url") != null ? body.get("url").toString() : null;
        if (url == null || url.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Field 'url' is required"));
        }
        int weight = 1;
        if (body.containsKey("weight")) {
            try {
                weight = Integer.parseInt(body.get("weight").toString());
            } catch (NumberFormatException ignored) {}
        }
        BackendServer server = backendRegistry.registerBackend(url, weight);
        healthCheckService.checkBackend(server);

        return ResponseEntity.ok(Map.of(
                "message", "Backend registered successfully",
                "backend", server
        ));
    }

    /**
     * Updates the weight of a registered backend dynamically at runtime.
     */
    @PostMapping("/backends/weight")
    public ResponseEntity<Map<String, Object>> updateBackendWeight(@RequestBody Map<String, Object> body) {
        String url = body.get("url") != null ? body.get("url").toString() : null;
        if (url == null || url.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Field 'url' is required"));
        }
        int weight = 1;
        if (body.containsKey("weight")) {
            try {
                weight = Integer.parseInt(body.get("weight").toString());
            } catch (NumberFormatException ignored) {}
        }
        boolean updated = backendRegistry.setBackendWeight(url, weight);
        if (updated) {
            return ResponseEntity.ok(Map.of(
                    "message", "Backend weight updated successfully",
                    "url", url,
                    "weight", weight
            ));
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Returns SingleFlight request coalescing telemetry and efficiency statistics.
     */
    @GetMapping("/coalescing")
    public ResponseEntity<Map<String, Object>> getCoalescingStats() {
        return ResponseEntity.ok(requestCoalescingService.getStats());
    }

    /**
     * Dynamically updates SingleFlight request coalescing settings.
     */
    @PostMapping("/coalescing")
    public ResponseEntity<Map<String, Object>> updateCoalescingConfig(
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) Long timeoutMs) {

        if (enabled != null) {
            requestCoalescingService.setEnabled(enabled);
        }
        if (timeoutMs != null) {
            requestCoalescingService.setTimeoutMs(timeoutMs);
        }

        Map<String, Object> res = new HashMap<>(requestCoalescingService.getStats());
        res.put("message", "Request coalescing configuration updated successfully");
        return ResponseEntity.ok(res);
    }

    /**
     * Deregisters a backend instance dynamically.
     */
    @DeleteMapping("/backends")
    public ResponseEntity<Map<String, Object>> removeBackend(@RequestParam String url) {
        boolean removed = backendRegistry.deregisterBackend(url);
        if (removed) {
            return ResponseEntity.ok(Map.of("message", "Backend deregistered", "url", url));
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Returns full circuit breaker telemetry for all backends.
     */
    @GetMapping("/circuit-breakers")
    public ResponseEntity<Map<String, Object>> getCircuitBreakers() {
        return ResponseEntity.ok(circuitBreakerManager.getTelemetry());
    }

    /**
     * Resets a specific backend circuit breaker or all circuit breakers to CLOSED.
     */
    @PostMapping("/circuit-breakers/reset")
    public ResponseEntity<Map<String, Object>> resetCircuitBreakers(@RequestBody(required = false) Map<String, String> body) {
        String url = (body != null) ? body.get("url") : null;
        if (url != null && !url.isBlank()) {
            circuitBreakerManager.reset(url);
            return ResponseEntity.ok(Map.of(
                    "message", "Circuit breaker for backend reset to CLOSED",
                    "backendUrl", url,
                    "telemetry", circuitBreakerManager.getCircuitBreaker(url).getTelemetry()
            ));
        } else {
            circuitBreakerManager.resetAll();
            return ResponseEntity.ok(Map.of(
                    "message", "All circuit breakers reset to CLOSED",
                    "telemetry", circuitBreakerManager.getTelemetry()
            ));
        }
    }

    /**
     * Manually trips a backend circuit breaker to OPEN for chaos drill / resilience testing.
     */
    @PostMapping("/circuit-breakers/trip")
    public ResponseEntity<Map<String, Object>> tripCircuitBreaker(@RequestBody Map<String, String> body) {
        String url = body.get("url");
        String reason = body.getOrDefault("reason", "Manual chaos simulation drill");
        if (url == null || url.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Field 'url' is required"));
        }
        circuitBreakerManager.trip(url, reason);
        return ResponseEntity.ok(Map.of(
                "message", "Circuit breaker manually tripped to OPEN",
                "backendUrl", url,
                "reason", reason,
                "telemetry", circuitBreakerManager.getCircuitBreaker(url).getTelemetry()
        ));
    }

    /**
     * Enables or disables circuit breaker enforcement.
     */
    @PostMapping("/circuit-breakers/toggle")
    public ResponseEntity<Map<String, Object>> toggleCircuitBreaker(@RequestBody(required = false) Map<String, Boolean> body) {
        boolean newState = (body != null && body.containsKey("enabled"))
                ? body.get("enabled")
                : !circuitBreakerManager.isEnabled();

        circuitBreakerManager.setEnabled(newState);
        return ResponseEntity.ok(Map.of(
                "message", "Circuit breaker enabled status updated",
                "enabled", circuitBreakerManager.isEnabled()
        ));
    }
}
