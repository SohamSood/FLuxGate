package com.fluxgate.controller;

import com.fluxgate.model.BackendServer;
import com.fluxgate.service.BackendRegistry;
import com.fluxgate.service.HealthCheckService;
import com.fluxgate.strategy.LoadBalancingStrategyManager;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Exposes administrative and monitoring endpoints for FluxGate itself.
 */
@RestController
@RequestMapping("/fluxgate")
public class FluxGateManagementController {

    private final BackendRegistry backendRegistry;
    private final HealthCheckService healthCheckService;
    private final LoadBalancingStrategyManager strategyManager;

    public FluxGateManagementController(BackendRegistry backendRegistry,
                                        HealthCheckService healthCheckService,
                                        LoadBalancingStrategyManager strategyManager) {
        this.backendRegistry = backendRegistry;
        this.healthCheckService = healthCheckService;
        this.strategyManager = strategyManager;
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

        return ResponseEntity.ok(response);
    }

    /**
     * Returns full status information for all registered backend instances.
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

        return ResponseEntity.ok(response);
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
