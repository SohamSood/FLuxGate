package com.fluxgate.service;

/**
 * Thrown when an upstream backend fails to respond within the configured timeout.
 */
public class GatewayTimeoutException extends RuntimeException {
    private final String backendUrl;

    public GatewayTimeoutException(String message, String backendUrl) {
        this(message, backendUrl, null);
    }

    public GatewayTimeoutException(String message, String backendUrl, Throwable cause) {
        super(message, cause);
        this.backendUrl = backendUrl;
    }

    public String getBackendUrl() {
        return backendUrl;
    }
}
