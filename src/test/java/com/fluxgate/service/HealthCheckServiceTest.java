package com.fluxgate.service;

import com.fluxgate.config.FluxGateProperties;
import com.fluxgate.model.BackendServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HealthCheckServiceTest {

    @Mock
    private HttpClient httpClient;

    @Mock
    private HttpResponse<Void> httpResponse;

    private BackendRegistry backendRegistry;
    private FluxGateProperties properties;
    private HealthCheckService healthCheckService;

    @BeforeEach
    void setUp() {
        properties = new FluxGateProperties();
        properties.getHealthCheck().setEnabled(true);
        properties.getHealthCheck().setHealthPath("/health");
        properties.getHealthCheck().setTimeoutMs(1000);

        backendRegistry = new BackendRegistry(properties);
        healthCheckService = new HealthCheckService(backendRegistry, httpClient, properties);
    }

    @Test
    @DisplayName("Should mark backend healthy when endpoint returns HTTP 200")
    void testCheckBackendHealthy() throws Exception {
        BackendServer backend = new BackendServer("http://backend:8081");
        backend.markUnhealthy("Initially down");

        when(httpResponse.statusCode()).thenReturn(200);
        doReturn(httpResponse).when(httpClient).send(any(HttpRequest.class), any());

        boolean result = healthCheckService.checkBackend(backend);
        assertTrue(result);
        assertTrue(backend.isHealthy());
    }

    @Test
    @DisplayName("Should mark backend unhealthy when endpoint returns HTTP 500")
    void testCheckBackend5xx() throws Exception {
        BackendServer backend = new BackendServer("http://backend:8081");

        when(httpResponse.statusCode()).thenReturn(500);
        doReturn(httpResponse).when(httpClient).send(any(HttpRequest.class), any());

        boolean result = healthCheckService.checkBackend(backend);
        assertFalse(result);
        assertFalse(backend.isHealthy());
        assertTrue(backend.getLastFailureReason().contains("HTTP status 500"));
    }

    @Test
    @DisplayName("Should mark backend unhealthy when health probe times out")
    void testCheckBackendTimeout() throws Exception {
        BackendServer backend = new BackendServer("http://backend:8081");

        doThrow(new HttpTimeoutException("timeout")).when(httpClient).send(any(HttpRequest.class), any());

        boolean result = healthCheckService.checkBackend(backend);
        assertFalse(result);
        assertFalse(backend.isHealthy());
        assertTrue(backend.getLastFailureReason().contains("timed out"));
    }

    @Test
    @DisplayName("Should mark backend unhealthy on general connection failure")
    void testCheckBackendConnectionFailure() throws Exception {
        BackendServer backend = new BackendServer("http://backend:8081");

        doThrow(new IOException("Connection refused")).when(httpClient).send(any(HttpRequest.class), any());

        boolean result = healthCheckService.checkBackend(backend);
        assertFalse(result);
        assertFalse(backend.isHealthy());
        assertTrue(backend.getLastFailureReason().contains("Connection refused"));
    }

    @Test
    @DisplayName("Should probe all backends during periodic check")
    void testRunPeriodicHealthChecks() throws Exception {
        backendRegistry.registerBackend("http://b1:8081");
        backendRegistry.registerBackend("http://b2:8082");

        when(httpResponse.statusCode()).thenReturn(200);
        doReturn(httpResponse).when(httpClient).send(any(HttpRequest.class), any());

        healthCheckService.runPeriodicHealthChecks();
        verify(httpClient, times(2)).send(any(HttpRequest.class), any());
    }

    @Test
    @DisplayName("Should skip health check when disabled in properties")
    void testDisabledHealthCheck() throws Exception {
        properties.getHealthCheck().setEnabled(false);
        backendRegistry.registerBackend("http://b1:8081");

        healthCheckService.runPeriodicHealthChecks();
        verify(httpClient, never()).send(any(), any());
    }
}
