package com.fluxgate.service;

import com.fluxgate.config.FluxGateProperties;
import com.fluxgate.model.CachedResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ResponseCacheServiceTest {

    private ResponseCacheService cacheService;
    private FluxGateProperties properties;

    @BeforeEach
    void setUp() {
        properties = new FluxGateProperties();
        properties.getCache().setEnabled(true);
        properties.getCache().setDefaultTtlSeconds(30);
        properties.getCache().setMaxEntries(10);
        cacheService = new ResponseCacheService(properties);
    }

    @Test
    void testPutAndGetCacheHit() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Content-Type", "application/json");
        byte[] body = "{\"message\":\"hello\"}".getBytes(StandardCharsets.UTF_8);

        cacheService.put("GET:/hello", 200, headers, body, "http://localhost:8081");

        Optional<CachedResponse> cachedOpt = cacheService.get("GET:/hello");
        assertTrue(cachedOpt.isPresent());

        CachedResponse cached = cachedOpt.get();
        assertEquals(200, cached.getStatusCode());
        assertArrayEquals(body, cached.getBody());
        assertEquals("http://localhost:8081", cached.getOriginBackend());
        assertEquals(1, cacheService.getHitCount());
        assertEquals(0, cacheService.getMissCount());
    }

    @Test
    void testCacheMissOnUnknownKey() {
        Optional<CachedResponse> cachedOpt = cacheService.get("GET:/nonexistent");
        assertFalse(cachedOpt.isPresent());
        assertEquals(0, cacheService.getHitCount());
        assertEquals(1, cacheService.getMissCount());
    }

    @Test
    void testCacheInvalidationByPath() {
        HttpHeaders headers = new HttpHeaders();
        byte[] body = "test".getBytes(StandardCharsets.UTF_8);

        cacheService.put("GET:/users/42", 200, headers, body, "http://backend-1");
        cacheService.put("GET:/users/42?details=true", 200, headers, body, "http://backend-1");
        cacheService.put("GET:/products/10", 200, headers, body, "http://backend-2");

        assertEquals(3, cacheService.getEntriesCount());

        int removed = cacheService.invalidatePath("/users/42");
        assertEquals(2, removed);
        assertEquals(1, cacheService.getEntriesCount());
        assertTrue(cacheService.get("GET:/products/10").isPresent());
    }

    @Test
    void testClearPurge() {
        HttpHeaders headers = new HttpHeaders();
        cacheService.put("GET:/a", 200, headers, new byte[0], "backend");
        cacheService.put("GET:/b", 200, headers, new byte[0], "backend");

        assertEquals(2, cacheService.getEntriesCount());

        cacheService.clear();
        assertEquals(0, cacheService.getEntriesCount());
    }

    @Test
    void testCacheDisabledToggle() {
        cacheService.setEnabled(false);
        HttpHeaders headers = new HttpHeaders();
        cacheService.put("GET:/hello", 200, headers, new byte[0], "backend");

        assertFalse(cacheService.get("GET:/hello").isPresent());
    }

    @Test
    void testStatsReporting() {
        HttpHeaders headers = new HttpHeaders();
        cacheService.put("GET:/hello", 200, headers, "{\"ok\":true}".getBytes(StandardCharsets.UTF_8), "backend");

        // 1 hit, 1 miss
        cacheService.get("GET:/hello");
        cacheService.get("GET:/missing");

        Map<String, Object> stats = cacheService.getStats();
        assertTrue((Boolean) stats.get("enabled"));
        assertEquals(1L, stats.get("hitCount"));
        assertEquals(1L, stats.get("missCount"));
        assertEquals(50.0, (Double) stats.get("hitRatioPercent"));
        assertEquals(1, stats.get("entriesCount"));
    }
}

