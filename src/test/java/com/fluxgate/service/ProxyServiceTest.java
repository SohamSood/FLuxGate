package com.fluxgate.service;

import com.fluxgate.config.FluxGateProperties;
import com.fluxgate.model.BackendServer;
import com.fluxgate.model.ProxyResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProxyServiceTest {

    @Mock
    private HttpClient httpClient;

    @Mock
    private HttpResponse<byte[]> httpResponse;

    private FluxGateProperties properties;
    private ProxyService proxyService;

    @BeforeEach
    void setUp() {
        properties = new FluxGateProperties();
        properties.getProxy().setReadTimeoutMs(5000);
        proxyService = new ProxyService(httpClient, properties);
    }

    @Test
    @DisplayName("Should forward request successfully and return enriched ProxyResponse")
    void testForwardSuccess() throws Exception {
        BackendServer backend = new BackendServer("http://upstream:8081");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/data");
        request.setQueryString("active=true");
        request.setRemoteAddr("10.0.0.1");

        HttpHeaders mockHeaders = HttpHeaders.of(
                Map.of("Content-Type", List.of("application/json"), "X-Custom", List.of("custom-val")),
                (k, v) -> true
        );
        byte[] responseBody = "{\"result\":\"success\"}".getBytes(StandardCharsets.UTF_8);

        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.headers()).thenReturn(mockHeaders);
        when(httpResponse.body()).thenReturn(responseBody);
        doReturn(httpResponse).when(httpClient).send(any(HttpRequest.class), any());

        ProxyResponse result = proxyService.forward(request, null, backend, "req-12345");

        assertNotNull(result);
        assertEquals(200, result.getStatusCode());
        assertArrayEquals(responseBody, result.getBody());
        assertEquals("FluxGate", result.getHeaders().getFirst("X-Proxy-By"));
        assertEquals("http://upstream:8081", result.getHeaders().getFirst("X-Upstream-Backend"));
        assertNotNull(result.getHeaders().getFirst("X-Proxy-Latency"));
        assertEquals(0, backend.getActiveRequests());
        assertEquals(1, backend.getTotalRequests());
    }

    @Test
    @DisplayName("Should throw GatewayTimeoutException when upstream times out")
    void testForwardTimeout() throws Exception {
        BackendServer backend = new BackendServer("http://upstream:8081");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/slow");

        doThrow(new HttpTimeoutException("Read timed out")).when(httpClient).send(any(HttpRequest.class), any());

        assertThrows(GatewayTimeoutException.class, () ->
                proxyService.forward(request, null, backend, "req-timeout")
        );

        assertFalse(backend.isHealthy());
        assertTrue(backend.getLastFailureReason().contains("Read timeout"));
        assertEquals(0, backend.getActiveRequests());
    }

    @Test
    @DisplayName("Should throw BadGatewayException when upstream connection fails")
    void testForwardBadGateway() throws Exception {
        BackendServer backend = new BackendServer("http://upstream:8081");
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/mutate");

        doThrow(new IOException("Connection refused")).when(httpClient).send(any(HttpRequest.class), any());

        assertThrows(BadGatewayException.class, () ->
                proxyService.forward(request, "body".getBytes(StandardCharsets.UTF_8), backend, "req-conn-err")
        );

        assertFalse(backend.isHealthy());
        assertTrue(backend.getLastFailureReason().contains("Connection failed"));
        assertEquals(0, backend.getActiveRequests());
    }
}
