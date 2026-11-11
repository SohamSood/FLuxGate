package com.fluxgate.model;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Immutable log record capturing an individual proxied or rate-limited ingress transaction.
 */
public class RequestLogEntry {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
            .withZone(ZoneId.systemDefault());

    private final long id;
    private final String timestamp;
    private final String clientIp;
    private final String method;
    private final String path;
    private final String targetBackend;
    private final int statusCode;
    private final long latencyMs;
    private final boolean rateLimited;

    public RequestLogEntry(long id, String clientIp, String method, String path,
                           String targetBackend, int statusCode, long latencyMs, boolean rateLimited) {
        this.id = id;
        this.timestamp = TIME_FORMAT.format(Instant.now());
        this.clientIp = clientIp != null ? clientIp : "unknown";
        this.method = method != null ? method : "GET";
        this.path = path != null ? path : "/";
        this.targetBackend = targetBackend != null ? targetBackend : "-";
        this.statusCode = statusCode;
        this.latencyMs = latencyMs;
        this.rateLimited = rateLimited;
    }

    public long getId() {
        return id;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public String getClientIp() {
        return clientIp;
    }

    public String getMethod() {
        return method;
    }

    public String getPath() {
        return path;
    }

    public String getTargetBackend() {
        return targetBackend;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public long getLatencyMs() {
        return latencyMs;
    }

    public boolean isRateLimited() {
        return rateLimited;
    }
}
