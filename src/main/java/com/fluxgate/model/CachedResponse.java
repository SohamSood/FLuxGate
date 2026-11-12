package com.fluxgate.model;

import org.springframework.http.HttpHeaders;

import java.time.Instant;
import java.util.Arrays;

/**
 * Immutable cached response object stored in the FluxGate edge cache.
 */
public class CachedResponse {

    private final int statusCode;
    private final HttpHeaders headers;
    private final byte[] body;
    private final String originBackend;
    private final Instant createdAt;
    private final Instant expiresAt;

    public CachedResponse(int statusCode, HttpHeaders headers, byte[] body, String originBackend, int ttlSeconds) {
        this.statusCode = statusCode;
        HttpHeaders copy = new HttpHeaders();
        if (headers != null) {
            headers.forEach((k, v) -> {
                // Strip hop-by-hop or previous cache age headers
                if (!"Age".equalsIgnoreCase(k) && !"X-Cache".equalsIgnoreCase(k)) {
                    copy.put(k, v);
                }
            });
        }
        this.headers = copy;
        this.body = body != null ? Arrays.copyOf(body, body.length) : new byte[0];
        this.originBackend = originBackend != null ? originBackend : "cache";
        this.createdAt = Instant.now();
        this.expiresAt = this.createdAt.plusSeconds(Math.max(1, ttlSeconds));
    }

    public int getStatusCode() {
        return statusCode;
    }

    public HttpHeaders getHeaders() {
        return headers;
    }

    public byte[] getBody() {
        return body != null ? Arrays.copyOf(body, body.length) : new byte[0];
    }

    public String getOriginBackend() {
        return originBackend;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    public long getAgeSeconds() {
        return Math.max(0, Instant.now().getEpochSecond() - createdAt.getEpochSecond());
    }

    public long getRemainingTtlSeconds() {
        return Math.max(0, expiresAt.getEpochSecond() - Instant.now().getEpochSecond());
    }
}
