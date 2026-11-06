package com.fluxgate.controller;

import com.fluxgate.model.BackendServer;
import com.fluxgate.service.BackendRegistry;
import com.fluxgate.service.HealthCheckService;
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

    public FluxGateManagementController(BackendRegistry backendRegistry, HealthCheckService healthCheckService) {
        this.backendRegistry = backendRegistry;
        this.healthCheckService = healthCheckService;
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
        response.put("strategy", "ROUND_ROBIN");
        response.put("healthyCount", healthy.size());
        response.put("totalCount", all.size());
        response.put("backends", all);

        return ResponseEntity.ok(response);
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
