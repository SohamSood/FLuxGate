package com.fluxgate.controller;

import com.fluxgate.config.FluxGateProperties;
import com.fluxgate.resilience.CircuitBreakerManager;
import com.fluxgate.service.*;
import com.fluxgate.strategy.RoundRobinStrategy;
import com.fluxgate.strategy.LoadBalancingStrategyManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DashboardControllerTest {

    private DashboardController controller;
    private BackendRegistry backendRegistry;

    @BeforeEach
    void setUp() {
        FluxGateProperties props = new FluxGateProperties();
        backendRegistry = new BackendRegistry(props);
        backendRegistry.registerBackend("http://backend:8081");

        RoundRobinStrategy rr = new RoundRobinStrategy();
        LoadBalancingStrategyManager strategyManager = new LoadBalancingStrategyManager(List.of(rr), rr);
        RateLimiterService rateLimiterService = new RateLimiterService(props);
        ResponseCacheService responseCacheService = new ResponseCacheService(props);
        TrafficMetricsService trafficMetricsService = new TrafficMetricsService();
        CircuitBreakerManager circuitBreakerManager = new CircuitBreakerManager(props);
        RequestCoalescingService requestCoalescingService = new RequestCoalescingService(props);

        controller = new DashboardController(
                backendRegistry,
                strategyManager,
                rateLimiterService,
                responseCacheService,
                trafficMetricsService,
                circuitBreakerManager,
                requestCoalescingService
        );
    }

    @Test
    @DisplayName("Should return HTML template containing FluxGate dashboard markup")
    void testGetDashboardHtml() {
        String html = controller.getDashboardHtml();
        assertNotNull(html);
        assertTrue(html.contains("<!DOCTYPE html>"));
        assertTrue(html.contains("FluxGate"));
        assertTrue(html.contains("Dashboard"));
    }

    @Test
    @DisplayName("Should return landing JSON with service status and endpoint directories")
    void testGetLandingJson() {
        ResponseEntity<Map<String, Object>> response = controller.getLandingJson();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals("FluxGate Reverse Proxy & Load Balancer", body.get("service"));
        assertEquals("1.4.0", body.get("version"));
        assertEquals("HEALTHY", body.get("status"));
        assertEquals(1, body.get("healthyBackends"));
        assertEquals(1, body.get("totalBackends"));
        assertTrue(body.containsKey("managementEndpoints"));
    }
}
