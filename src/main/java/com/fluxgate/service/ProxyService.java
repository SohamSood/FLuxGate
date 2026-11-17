package com.fluxgate.service;

import com.fluxgate.config.FluxGateProperties;
import com.fluxgate.model.BackendServer;
import com.fluxgate.model.ProxyResponse;
import com.fluxgate.util.ProxyHeaderUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;

/**
 * Executes transparent HTTP request forwarding to an upstream backend and returns the response.
 */
@Service
public class ProxyService {

    private static final Logger log = LoggerFactory.getLogger(ProxyService.class);

    private final HttpClient httpClient;
    private final FluxGateProperties properties;

    public ProxyService(HttpClient httpClient, FluxGateProperties properties) {
        this.httpClient = httpClient;
        this.properties = properties;
    }

    /**
     * Forwards the incoming request to the chosen backend server.
     */
    public ProxyResponse forward(HttpServletRequest request, byte[] body, BackendServer backend) {
        return forward(request, body, backend, ProxyHeaderUtils.resolveRequestId(request));
    }

    /**
     * Forwards the incoming request with distributed tracing correlation ID.
     */
    public ProxyResponse forward(HttpServletRequest request, byte[] body, BackendServer backend, String requestId) {
        String method = request.getMethod();
        String requestUri = request.getRequestURI();
        String queryString = request.getQueryString();

        String targetUrl = ProxyHeaderUtils.buildTargetUrl(backend.getUrl(), requestUri, queryString);
        long startTime = System.currentTimeMillis();

        backend.incrementRequests();

        try {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(targetUrl))
                    .timeout(Duration.ofMillis(properties.getProxy().getReadTimeoutMs()));

            // Copy allowed headers and inject standard X-Forwarded-* and X-Request-Id headers
            ProxyHeaderUtils.copyRequestHeaders(request, requestBuilder, backend, requestId);

            // Set HTTP method and body payload
            setMethodAndBody(requestBuilder, method, body);

            HttpRequest upstreamRequest = requestBuilder.build();
            HttpResponse<byte[]> upstreamResponse = httpClient.send(upstreamRequest, HttpResponse.BodyHandlers.ofByteArray());

            long latencyMs = System.currentTimeMillis() - startTime;
            int statusCode = upstreamResponse.statusCode();

            // Extract response headers and enrich with proxy diagnostic telemetry
            HttpHeaders responseHeaders = ProxyHeaderUtils.extractResponseHeaders(upstreamResponse.headers().map());
            responseHeaders.set("X-Proxy-By", "FluxGate");
            responseHeaders.set("X-Upstream-Backend", backend.getUrl());
            responseHeaders.set("X-Proxy-Latency", latencyMs + "ms");

            log.info("[PROXY] RequestId={} Method={} Path={} Query={} Backend={} Status={} Latency={}ms",
                    requestId, method, requestUri, (queryString != null ? queryString : "-"), backend.getUrl(), statusCode, latencyMs);

            return new ProxyResponse(statusCode, responseHeaders, upstreamResponse.body(), latencyMs);

        } catch (HttpTimeoutException te) {
            long latencyMs = System.currentTimeMillis() - startTime;
            backend.markUnhealthy("Read timeout after " + latencyMs + "ms");
            log.error("[TIMEOUT] Backend {} timed out serving {} {} after {}ms",
                    backend.getUrl(), method, requestUri, latencyMs);
            throw new GatewayTimeoutException(
                    "Backend timed out after " + properties.getProxy().getReadTimeoutMs() + "ms",
                    backend.getUrl(),
                    te
            );

        } catch (IOException | InterruptedException ioEx) {
            long latencyMs = System.currentTimeMillis() - startTime;
            backend.markUnhealthy("Connection failed: " + ioEx.getMessage());
            log.error("[BAD GATEWAY] Connection failed to backend {} for {} {}: {}",
                    backend.getUrl(), method, requestUri, ioEx.getMessage());

            if (ioEx instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }

            throw new BadGatewayException(
                    "Failed to communicate with upstream backend: " + ioEx.getMessage(),
                    backend.getUrl(),
                    ioEx
            );
        } finally {
            backend.decrementActiveRequests();
        }
    }

    private void setMethodAndBody(HttpRequest.Builder builder, String method, byte[] body) {
        HttpRequest.BodyPublisher publisher;
        if (body != null && body.length > 0) {
            publisher = HttpRequest.BodyPublishers.ofByteArray(body);
        } else {
            publisher = HttpRequest.BodyPublishers.noBody();
        }

        builder.method(method.toUpperCase(), publisher);
    }
}
