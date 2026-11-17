package com.fluxgate.controller;

import com.fluxgate.model.ErrorResponse;
import com.fluxgate.service.BadGatewayException;
import com.fluxgate.service.GatewayTimeoutException;
import com.fluxgate.service.NoHealthyBackendException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.*;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
        request = new MockHttpServletRequest();
        request.setRequestURI("/test-endpoint");
    }

    @Test
    @DisplayName("Should return 503 SERVICE_UNAVAILABLE for NoHealthyBackendException")
    void testHandleNoHealthyBackend() {
        NoHealthyBackendException ex = new NoHealthyBackendException("No healthy backends");
        ResponseEntity<ErrorResponse> response = handler.handleNoHealthyBackend(ex, request);

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(503, response.getBody().getStatus());
        assertEquals("Service Unavailable", response.getBody().getError());
        assertEquals("No healthy backends", response.getBody().getMessage());
        assertEquals("/test-endpoint", response.getBody().getPath());
    }

    @Test
    @DisplayName("Should return 502 BAD_GATEWAY for BadGatewayException")
    void testHandleBadGateway() {
        BadGatewayException ex = new BadGatewayException("Failed to connect", "http://backend:8081");
        ResponseEntity<ErrorResponse> response = handler.handleBadGateway(ex, request);

        assertEquals(HttpStatus.BAD_GATEWAY, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(502, response.getBody().getStatus());
        assertEquals("Bad Gateway", response.getBody().getError());
        assertEquals("http://backend:8081", response.getBody().getBackend());
    }

    @Test
    @DisplayName("Should return 504 GATEWAY_TIMEOUT for GatewayTimeoutException")
    void testHandleGatewayTimeout() {
        GatewayTimeoutException ex = new GatewayTimeoutException("Backend timed out", "http://backend:8081");
        ResponseEntity<ErrorResponse> response = handler.handleGatewayTimeout(ex, request);

        assertEquals(HttpStatus.GATEWAY_TIMEOUT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(504, response.getBody().getStatus());
        assertEquals("Gateway Timeout", response.getBody().getError());
        assertEquals("http://backend:8081", response.getBody().getBackend());
    }

    @Test
    @DisplayName("Should return 500 INTERNAL_SERVER_ERROR for general Exception")
    void testHandleGeneralException() {
        Exception ex = new RuntimeException("Unexpected bug");
        ResponseEntity<ErrorResponse> response = handler.handleGeneralException(ex, request);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(500, response.getBody().getStatus());
        assertEquals("Internal Server Error", response.getBody().getError());
    }
}
