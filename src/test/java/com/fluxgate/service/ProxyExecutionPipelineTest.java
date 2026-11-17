package com.fluxgate.service;

import com.fluxgate.config.FluxGateProperties;
import com.fluxgate.model.BackendServer;
import com.fluxgate.model.ProxyPipelineResult;
import com.fluxgate.model.ProxyResponse;
import com.fluxgate.resilience.CircuitBreakerManager;
import com.fluxgate.strategy.RoundRobinStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProxyExecutionPipelineTest {

    @Mock
    private ProxyService proxyService;

    private BackendRegistry backendRegistry;
    private CircuitBreakerManager circuitBreakerManager;
    private ResponseCacheService responseCacheService;
    private RequestCoalescingService requestCoalescingService;
    private ProxyExecutionPipeline pipeline;

    @BeforeEach
    void setUp() {
        FluxGateProperties properties = new FluxGateProperties();
        properties.getCircuitBreaker().setEnabled(true);
        properties.getCircuitBreaker().setFailureThreshold(3);
        properties.getCoalescing().setEnabled(false); // disable coalescing for direct unit pipeline test
        properties.getCache().setEnabled(true);

        backendRegistry = new BackendRegistry(properties);
        circuitBreakerManager = new CircuitBreakerManager(properties);
        responseCacheService = new ResponseCacheService(properties);
        requestCoalescingService = new RequestCoalescingService(properties);

        RoundRobinStrategy strategy = new RoundRobinStrategy();

        pipeline = new ProxyExecutionPipeline(
                backendRegistry,
                strategy,
                proxyService,
                circuitBreakerManager,
                responseCacheService,
                requestCoalescingService
        );
    }

    @Test
    @DisplayName("Should dispatch request and return ProxyPipelineResult")
    void testDispatchSuccess() {
        backendRegistry.registerBackend("http://backend:8081");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/resource");

        ProxyResponse mockResponse = new ProxyResponse(200, new HttpHeaders(), new byte[0], 25L);
        when(proxyService.forward(any(), any(), any(), any())).thenReturn(mockResponse);

        ProxyPipelineResult result = pipeline.dispatch(request, null, "GET:/api/resource", true, false, "req-1");

        assertNotNull(result);
        assertEquals(200, result.getResponse().getStatusCode());
        assertEquals("http://backend:8081", result.getOriginBackend());
        assertEquals("MISS", result.getCacheStatus());
        assertEquals("CLOSED", result.getCircuitBreakerState());
        assertEquals(0, result.getFailoverAttempts());
    }

    @Test
    @DisplayName("Should transparently failover when primary backend returns HTTP 500")
    void testFailoverOn5xx() {
        backendRegistry.registerBackend("http://b1:8081");
        backendRegistry.registerBackend("http://b2:8082");

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/failover");

        ProxyResponse errorResponse = new ProxyResponse(500, new HttpHeaders(), new byte[0], 30L);
        ProxyResponse successResponse = new ProxyResponse(200, new HttpHeaders(), new byte[0], 20L);

        when(proxyService.forward(any(), any(), eq(backendRegistry.findByUrl("http://b1:8081").get()), any()))
                .thenReturn(errorResponse);
        when(proxyService.forward(any(), any(), eq(backendRegistry.findByUrl("http://b2:8082").get()), any()))
                .thenReturn(successResponse);

        ProxyPipelineResult result = pipeline.dispatch(request, null, "GET:/api/failover", true, false, "req-failover");

        assertNotNull(result);
        assertEquals(200, result.getResponse().getStatusCode());
        assertEquals("http://b2:8082", result.getOriginBackend());
        assertEquals(1, result.getFailoverAttempts());
        assertEquals("http://b1:8081", result.getFailoverOrigin());
    }

    @Test
    @DisplayName("Should throw NoHealthyBackendException when no backends are registered")
    void testNoBackendsThrows() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/empty");
        assertThrows(NoHealthyBackendException.class, () ->
                pipeline.dispatch(request, null, "GET:/api/empty", true, false, "req-none")
        );
    }
}
