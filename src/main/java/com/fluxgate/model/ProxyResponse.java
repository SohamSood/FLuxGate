package com.fluxgate.model;

import org.springframework.http.HttpHeaders;

/**
 * Encapsulates the response received from an upstream backend server.
 */
public class ProxyResponse {

    private final int statusCode;
    private final HttpHeaders headers;
    private final byte[] body;
    private final long latencyMs;

    public ProxyResponse(int statusCode, HttpHeaders headers, byte[] body, long latencyMs) {
        this.statusCode = statusCode;
        this.headers = headers != null ? headers : new HttpHeaders();
        this.body = body != null ? body : new byte[0];
        this.latencyMs = latencyMs;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public HttpHeaders getHeaders() {
        return headers;
    }

    public byte[] getBody() {
        return body;
    }

    public long getLatencyMs() {
        return latencyMs;
    }
}
