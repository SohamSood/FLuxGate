package com.fluxgate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ManagementEndpointsIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    private String getBaseUrl() {
        return "http://localhost:" + port;
    }

    @Test
    @DisplayName("GET /fluxgate/health should return system UP status and subsystem indicators")
    void testHealthEndpoint() {
        ResponseEntity<Map> response = restTemplate.getForEntity(getBaseUrl() + "/fluxgate/health", Map.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("UP", response.getBody().get("status"));
        assertEquals("FluxGate", response.getBody().get("service"));
        assertTrue((Boolean) response.getBody().get("ipFilterEnabled"));
        assertTrue(response.getBody().containsKey("healthyBackends"));
    }

    @Test
    @DisplayName("GET /fluxgate/status should return comprehensive cluster and telemetry status")
    void testStatusEndpoint() {
        ResponseEntity<Map> response = restTemplate.getForEntity(getBaseUrl() + "/fluxgate/status", Map.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().containsKey("backends"));
        assertTrue(response.getBody().containsKey("rateLimiter"));
        assertTrue(response.getBody().containsKey("cache"));
        assertTrue(response.getBody().containsKey("ipFilter"));
        assertTrue(response.getBody().containsKey("circuitBreakers"));
        assertTrue(response.getBody().containsKey("metrics"));
    }

    @Test
    @DisplayName("GET & POST /fluxgate/strategy should retrieve and dynamically switch load balancing algorithm")
    void testStrategyEndpoints() {
        // 1. Get current strategy
        ResponseEntity<Map> getResp = restTemplate.getForEntity(getBaseUrl() + "/fluxgate/strategy", Map.class);
        assertEquals(HttpStatus.OK, getResp.getStatusCode());
        assertNotNull(getResp.getBody().get("active"));

        // 2. Switch to LEAST_CONNECTIONS
        ResponseEntity<Map> postResp = restTemplate.postForEntity(
                getBaseUrl() + "/fluxgate/strategy?name=LEAST_CONNECTIONS", null, Map.class
        );
        assertEquals(HttpStatus.OK, postResp.getStatusCode());
        assertEquals("LEAST_CONNECTIONS", postResp.getBody().get("active"));

        // 3. Reset back to ROUND_ROBIN
        restTemplate.postForEntity(getBaseUrl() + "/fluxgate/strategy?name=ROUND_ROBIN", null, Map.class);
    }

    @Test
    @DisplayName("GET & POST /fluxgate/rate-limit should inspect and tune token bucket parameters")
    void testRateLimitEndpoints() {
        // 1. Get current rate limit stats
        ResponseEntity<Map> getResp = restTemplate.getForEntity(getBaseUrl() + "/fluxgate/rate-limit", Map.class);
        assertEquals(HttpStatus.OK, getResp.getStatusCode());
        assertTrue((Boolean) getResp.getBody().get("enabled"));

        // 2. Update rate limit capacity
        ResponseEntity<Map> postResp = restTemplate.postForEntity(
                getBaseUrl() + "/fluxgate/rate-limit?capacity=120&refillRatePerMinute=90", null, Map.class
        );
        assertEquals(HttpStatus.OK, postResp.getStatusCode());
        assertEquals(120, postResp.getBody().get("capacity"));
        assertEquals(90, postResp.getBody().get("refillRatePerMinute"));

        // 3. Reset back
        restTemplate.postForEntity(getBaseUrl() + "/fluxgate/rate-limit?capacity=60&refillRatePerMinute=60", null, Map.class);
    }

    @Test
    @DisplayName("GET, POST & DELETE /fluxgate/cache should manage edge cache lifecycle and purge entries")
    void testCacheEndpoints() {
        // 1. Get cache stats
        ResponseEntity<Map> getResp = restTemplate.getForEntity(getBaseUrl() + "/fluxgate/cache", Map.class);
        assertEquals(HttpStatus.OK, getResp.getStatusCode());

        // 2. Update default TTL
        ResponseEntity<Map> postResp = restTemplate.postForEntity(
                getBaseUrl() + "/fluxgate/cache?defaultTtlSeconds=45", null, Map.class
        );
        assertEquals(HttpStatus.OK, postResp.getStatusCode());
        assertEquals(45, postResp.getBody().get("defaultTtlSeconds"));

        // 3. Purge cache
        restTemplate.delete(getBaseUrl() + "/fluxgate/cache");
    }

    @Test
    @DisplayName("GET & POST /fluxgate/ip-filter should manage firewall blocklist dynamically")
    void testIpFilterEndpoints() {
        String testIp = "198.51.100.77";

        // 1. Get IP filter status
        ResponseEntity<Map> getResp = restTemplate.getForEntity(getBaseUrl() + "/fluxgate/ip-filter", Map.class);
        assertEquals(HttpStatus.OK, getResp.getStatusCode());
        assertTrue((Boolean) getResp.getBody().get("enabled"));

        // 2. Block IP
        ResponseEntity<Map> blockResp = restTemplate.postForEntity(
                getBaseUrl() + "/fluxgate/ip-filter/block?ip=" + testIp, null, Map.class
        );
        assertEquals(HttpStatus.OK, blockResp.getStatusCode());
        assertEquals(true, blockResp.getBody().get("blocked"));

        // 3. Unblock IP
        ResponseEntity<Map> unblockResp = restTemplate.postForEntity(
                getBaseUrl() + "/fluxgate/ip-filter/unblock?ip=" + testIp, null, Map.class
        );
        assertEquals(HttpStatus.OK, unblockResp.getStatusCode());
        assertEquals(true, unblockResp.getBody().get("unblocked"));
    }

    @Test
    @DisplayName("GET /fluxgate/metrics should return traffic metrics and recent transaction buffer")
    void testMetricsEndpoint() {
        ResponseEntity<Map> response = restTemplate.getForEntity(getBaseUrl() + "/fluxgate/metrics", Map.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().containsKey("totalRequests"));
        assertTrue(response.getBody().containsKey("recentRequests"));
    }

    @Test
    @DisplayName("GET /dashboard should render visual HTML console")
    void testDashboardView() {
        ResponseEntity<String> response = restTemplate.getForEntity(getBaseUrl() + "/dashboard", String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getHeaders().getContentType().toString().contains("text/html"));
        assertTrue(response.getBody().contains("FluxGate"));
        assertTrue(response.getBody().contains("Dashboard"));
    }
}
