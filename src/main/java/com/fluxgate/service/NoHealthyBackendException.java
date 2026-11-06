package com.fluxgate.service;

/**
 * Thrown when no healthy backend instances are available to serve a request.
 */
public class NoHealthyBackendException extends RuntimeException {
    public NoHealthyBackendException(String message) {
        super(message);
    }
}
