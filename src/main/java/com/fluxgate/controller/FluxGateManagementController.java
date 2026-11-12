package com.fluxgate.controller;

import com.fluxgate.model.BackendServer;
import com.fluxgate.service.BackendRegistry;
import com.fluxgate.service.HealthCheckService;
import com.fluxgate.service.RateLimiterService;
import com.fluxgate.service.ResponseCacheService;
import com.fluxgate.service.TrafficMetricsService;
import com.fluxgate.strategy.LoadBalancingStrategyManager;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Exposes administrative, rate-limiting, edge cache, and telemetry monitoring endpoints for FluxGate.
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

    public FluxGateManagementController(BackendRegistry backendRegistry,
                                        HealthCheckService healthCheckService,
                                        LoadBalancingStrategyManager strategyManager,
                                        RateLimiterService rateLimiterService,
                                        ResponseCacheService responseCacheService,
                                        TrafficMetricsService trafficMetricsService) {
        this.backendRegistry = backendRegistry;
        this.healthCheckService = healthCheckService;
        this.strategyManager = strategyManager;
        this.rateLimiterService = rateLimiterService;
        this.responseCacheService = responseCacheService;
        this.trafficMetricsService = trafficMetricsService;
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

        return ResponseEntity.ok(response);
    }

    /**
     * Returns full status information for all registered backend instances, rate limiter, and edge cache.
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
        response.put("metrics", Map.of(
                "totalRequests", trafficMetricsService.getTotalRequests(),
                "averageLatencyMs", trafficMetricsService.getAverageLatencyMs(),
                "status2xx", trafficMetricsService.getTotal2xx(),
                "status4xx", trafficMetricsService.getTotal4xx(),
                "status5xx", trafficMetricsService.getTotal5xx(),
                "cacheHits", trafficMetricsService.getTotalCacheHits()
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
     * Registers a new backend URL dynamically at runtime.
     */
    @PostMapping("/backends")
    public ResponseEntity<Map<String, Object>> addBackend(@RequestBody Map<String, String> body) {
        String url = body.get("url");
        if (url == null || url.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Field 'url' is required"));
        }
        BackendServer server = backendRegistry.registerBackend(url);
        healthCheckService.checkBackend(server);

        return ResponseEntity.ok(Map.of(
                "message", "Backend registered successfully",
                "backend", server
        ));
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
}
