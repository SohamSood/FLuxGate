package com.fluxgate.service;

/**
 * Thrown when an upstream backend returns an unrecoverable connection failure.
 */
public class BadGatewayException extends RuntimeException {
    private final String backendUrl;

    public BadGatewayException(String message, String backendUrl, Throwable cause) {
        super(message, cause);
        this.backendUrl = backendUrl;
    }

    public String getBackendUrl() {
        return backendUrl;
    }
}
