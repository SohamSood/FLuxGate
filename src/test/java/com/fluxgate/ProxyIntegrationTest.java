package com.fluxgate;

import com.fluxgate.service.BackendRegistry;
import com.fluxgate.service.HealthCheckService;
import com.fluxgate.strategy.RoundRobinStrategy;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProxyIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private BackendRegistry backendRegistry;

    @Autowired
    private HealthCheckService healthCheckService;

    @Autowired
    private RoundRobinStrategy roundRobinStrategy;

    @Autowired
    private com.fluxgate.service.ResponseCacheService responseCacheService;

    private static HttpServer server1;
    private static HttpServer server2;
    private static HttpServer server3;

    private static String url1;
    private static String url2;
    private static String url3;

    private static final AtomicInteger server1HitCount = new AtomicInteger(0);
    private static final AtomicInteger server2HitCount = new AtomicInteger(0);
    private static final AtomicInteger server3HitCount = new AtomicInteger(0);
    private static final List<String> capturedBodies = new CopyOnWriteArrayList<>();

    @BeforeAll
    static void startMockBackends() throws IOException {
        server1 = createMockServer(1, server1HitCount);
        server2 = createMockServer(2, server2HitCount);
        server3 = createMockServer(3, server3HitCount);

        url1 = "http://localhost:" + server1.getAddress().getPort();
        url2 = "http://localhost:" + server2.getAddress().getPort();
        url3 = "http://localhost:" + server3.getAddress().getPort();
    }

    @BeforeEach
    void resetState() {
        backendRegistry.clear();
        backendRegistry.registerBackend(url1);
        backendRegistry.registerBackend(url2);
        backendRegistry.registerBackend(url3);
        roundRobinStrategy.reset();
        if (responseCacheService != null) {
            responseCacheService.clear();
            responseCacheService.setEnabled(false);
        }

        server1HitCount.set(0);
        server2HitCount.set(0);
        server3HitCount.set(0);
        capturedBodies.clear();
    }

    private static HttpServer createMockServer(int id, AtomicInteger hitCounter) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);

        // /health endpoint
        server.createContext("/health", exchange -> {
            byte[] response = ("{\"status\":\"UP\",\"backend\":" + id + "}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
        });

        // /api/echo endpoint
        server.createContext("/api/echo", exchange -> {
            hitCounter.incrementAndGet();
            String query = exchange.getRequestURI().getQuery();
            byte[] bodyBytes = exchange.getRequestBody().readAllBytes();
            if (bodyBytes.length > 0) {
                capturedBodies.add(new String(bodyBytes, StandardCharsets.UTF_8));
            }

            String customHeader = exchange.getRequestHeaders().getFirst("X-Custom-Header");
            exchange.getResponseHeaders().set("X-Backend-Id", String.valueOf(id));
            if (customHeader != null) {
                exchange.getResponseHeaders().set("X-Echoed-Custom", customHeader);
            }
            exchange.getResponseHeaders().set("Content-Type", "application/json");

            String responseText = "{\"backend\":" + id + ",\"query\":\"" + (query != null ? query : "") + "\"}";
            byte[] responseBytes = responseText.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, responseBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseBytes);
            }
        });

        server.setExecutor(null);
        server.start();
        return server;
    }

    @AfterAll
    static void stopMockBackends() {
        if (server1 != null) server1.stop(0);
        if (server2 != null) server2.stop(0);
        if (server3 != null) server3.stop(0);
    }

    private String getBaseUrl() {
        return "http://localhost:" + port;
    }

    @Test
    @DisplayName("Should distribute requests evenly across 3 healthy backends via Round Robin")
    void testRoundRobinDistribution() {
        int totalRequests = 6;
        for (int i = 0; i < totalRequests; i++) {
            ResponseEntity<String> response = restTemplate.getForEntity(getBaseUrl() + "/api/echo", String.class);
            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getHeaders().getFirst("X-Backend-Id"));
        }

        assertEquals(2, server1HitCount.get(), "Server 1 should have 2 hits");
        assertEquals(2, server2HitCount.get(), "Server 2 should have 2 hits");
        assertEquals(2, server3HitCount.get(), "Server 3 should have 2 hits");
    }

    @Test
    @DisplayName("Should preserve query parameters, headers, and request body")
    void testPreserveRequestAttributes() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Custom-Header", "FluxGate-Test-Value");

        String requestJson = "{\"action\":\"proxy-test\",\"timestamp\":123456}";
        HttpEntity<String> entity = new HttpEntity<>(requestJson, headers);

        ResponseEntity<String> response = restTemplate.exchange(
                getBaseUrl() + "/api/echo?filter=active&limit=10",
                HttpMethod.POST,
                entity,
                String.class
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().contains("\"query\":\"filter=active&limit=10\""));
        assertEquals("FluxGate-Test-Value", response.getHeaders().getFirst("X-Echoed-Custom"));
        assertTrue(capturedBodies.contains(requestJson));
    }

    @Test
    @DisplayName("Should route only to healthy backends when one backend is marked unhealthy")
    void testRouteOnlyToHealthy() {
        // Mark server2 as unhealthy
        backendRegistry.findByUrl(url2).orElseThrow().markUnhealthy("Simulated failure");

        for (int i = 0; i < 4; i++) {
            ResponseEntity<String> response = restTemplate.getForEntity(getBaseUrl() + "/api/echo", String.class);
            assertEquals(HttpStatus.OK, response.getStatusCode());
        }

        assertEquals(2, server1HitCount.get(), "Server 1 should have 2 hits");
        assertEquals(0, server2HitCount.get(), "Unhealthy Server 2 should receive 0 hits");
        assertEquals(2, server3HitCount.get(), "Server 3 should have 2 hits");
    }

    @Test
    @DisplayName("Should return 503 Service Unavailable when all backends are unhealthy")
    void testNoHealthyBackends() {
        backendRegistry.findByUrl(url1).orElseThrow().markUnhealthy("down");
        backendRegistry.findByUrl(url2).orElseThrow().markUnhealthy("down");
        backendRegistry.findByUrl(url3).orElseThrow().markUnhealthy("down");

        ResponseEntity<Map> response = restTemplate.getForEntity(getBaseUrl() + "/api/echo", Map.class);
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertEquals(503, response.getBody().get("status"));
        assertEquals("Service Unavailable", response.getBody().get("error"));
    }

    @Test
    @DisplayName("Should expose FluxGate self-health and status endpoints")
    void testFluxGateManagementEndpoints() {
        ResponseEntity<Map> healthResp = restTemplate.getForEntity(getBaseUrl() + "/fluxgate/health", Map.class);
        assertEquals(HttpStatus.OK, healthResp.getStatusCode());
        assertEquals("UP", healthResp.getBody().get("status"));
        assertEquals("FluxGate", healthResp.getBody().get("service"));

        ResponseEntity<Map> statusResp = restTemplate.getForEntity(getBaseUrl() + "/fluxgate/status", Map.class);
        assertEquals(HttpStatus.OK, statusResp.getStatusCode());
        assertEquals("ROUND_ROBIN", statusResp.getBody().get("strategy"));
        assertEquals(3, (int) statusResp.getBody().get("totalCount"));
    }

    @Test
    @DisplayName("Should cache idempotent GET responses and serve sub-millisecond cache hits")
    void testEdgeCachingIntegration() {
        responseCacheService.setEnabled(true);
        try {
            // First request: Cache Miss
            ResponseEntity<String> res1 = restTemplate.getForEntity(getBaseUrl() + "/api/echo?cacheTest=1", String.class);
            assertEquals(HttpStatus.OK, res1.getStatusCode());
            assertEquals("MISS", res1.getHeaders().getFirst("X-Cache"));

            // Second request: Cache Hit
            ResponseEntity<String> res2 = restTemplate.getForEntity(getBaseUrl() + "/api/echo?cacheTest=1", String.class);
            assertEquals(HttpStatus.OK, res2.getStatusCode());
            assertEquals("HIT", res2.getHeaders().getFirst("X-Cache"));
            assertEquals("0ms (Edge Cache)", res2.getHeaders().getFirst("X-Proxy-Latency"));

            // Verify cache stats endpoint
            ResponseEntity<Map> cacheResp = restTemplate.getForEntity(getBaseUrl() + "/fluxgate/cache", Map.class);
            assertEquals(HttpStatus.OK, cacheResp.getStatusCode());
            assertEquals(true, cacheResp.getBody().get("enabled"));
            assertEquals(1, (int) cacheResp.getBody().get("hitCount"));
        } finally {
            responseCacheService.setEnabled(false);
        }
    }
}
