package com.fluxgate.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class CachedResponseTest {

    @Test
    @DisplayName("Should initialize cached response and strip Age and X-Cache headers")
    void testInitializationAndHeaderStripping() {
        HttpHeaders incoming = new HttpHeaders();
        incoming.set("Content-Type", "application/json");
        incoming.set("Age", "120");
        incoming.set("X-Cache", "HIT");
        incoming.set("X-Custom", "preserve-this");

        byte[] payload = "{\"status\":\"ok\"}".getBytes(StandardCharsets.UTF_8);
        CachedResponse cached = new CachedResponse(200, incoming, payload, "http://origin:8081", 60);

        assertEquals(200, cached.getStatusCode());
        assertEquals("http://origin:8081", cached.getOriginBackend());
        assertArrayEquals(payload, cached.getBody());

        HttpHeaders headers = cached.getHeaders();
        assertEquals("application/json", headers.getFirst("Content-Type"));
        assertEquals("preserve-this", headers.getFirst("X-Custom"));
        assertNull(headers.getFirst("Age"), "Age header should be stripped");
        assertNull(headers.getFirst("X-Cache"), "X-Cache header should be stripped");
    }

    @Test
    @DisplayName("Should determine TTL, remaining time, and expiration")
    void testExpirationLogic() {
        CachedResponse fresh = new CachedResponse(200, new HttpHeaders(), new byte[0], "http://origin:8081", 60);
        assertFalse(fresh.isExpired());
        assertTrue(fresh.getRemainingTtlSeconds() > 0);
        assertEquals(0, fresh.getAgeSeconds());

        // Zero or negative TTL defaults to at least 1 second
        CachedResponse minimal = new CachedResponse(200, null, null, null, 0);
        assertEquals("cache", minimal.getOriginBackend());
        assertNotNull(minimal.getBody());
        assertEquals(0, minimal.getBody().length);
    }
}
