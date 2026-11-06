package com.fluxgate.controller;

import com.fluxgate.model.BackendServer;
import com.fluxgate.model.ProxyResponse;
import com.fluxgate.service.BackendRegistry;
import com.fluxgate.service.NoHealthyBackendException;
import com.fluxgate.service.ProxyService;
import com.fluxgate.strategy.LoadBalancingStrategy;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Catch-all controller intercepting client requests, selecting a healthy backend,
 * and forwarding the request.
 */
@RestController
public class ReverseProxyController {

    private final BackendRegistry backendRegistry;
    private final LoadBalancingStrategy loadBalancingStrategy;
    private final ProxyService proxyService;

    public ReverseProxyController(BackendRegistry backendRegistry,
                                  LoadBalancingStrategy loadBalancingStrategy,
                                  ProxyService proxyService) {
        this.backendRegistry = backendRegistry;
        this.loadBalancingStrategy = loadBalancingStrategy;
        this.proxyService = proxyService;
    }

    @RequestMapping(value = "/**")
    public ResponseEntity<byte[]> handleRequest(
            HttpServletRequest request,
            @RequestBody(required = false) byte[] body) {

        BackendServer backend = loadBalancingStrategy.select(backendRegistry.getAllBackends())
                .orElseThrow(() -> new NoHealthyBackendException(
                        "No healthy backend instances available to handle the request: " + request.getRequestURI()));

        ProxyResponse proxyResponse = proxyService.forward(request, body, backend);

        return ResponseEntity
                .status(proxyResponse.getStatusCode())
                .headers(proxyResponse.getHeaders())
                .body(proxyResponse.getBody());
    }
}
