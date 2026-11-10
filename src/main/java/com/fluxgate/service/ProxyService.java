package com.fluxgate.service;

import com.fluxgate.config.FluxGateProperties;
import com.fluxgate.model.BackendServer;
import com.fluxgate.model.ProxyResponse;
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
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Executes transparent HTTP request forwarding to an upstream backend and returns the response.
 */
@Service
public class ProxyService {

    private static final Logger log = LoggerFactory.getLogger(ProxyService.class);

    // Hop-by-hop headers that must not be forwarded by a proxy (RFC 7230 §6.1)
    private static final Set<String> HOP_BY_HOP_HEADERS = Set.of(
            "connection",
            "keep-alive",
            "proxy-authenticate",
            "proxy-authorization",
            "te",
            "trailer",
            "trailers",
            "transfer-encoding",
            "upgrade",
            "host",
            "content-length"
    );

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
        String method = request.getMethod();
        String requestUri = request.getRequestURI();
        String queryString = request.getQueryString();

        String targetUrl = backend.getUrl() + requestUri + (queryString != null && !queryString.isBlank() ? "?" + queryString : "");
        long startTime = System.currentTimeMillis();

        backend.incrementRequests();

        try {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(targetUrl))
                    .timeout(Duration.ofMillis(properties.getProxy().getReadTimeoutMs()));

            // Copy allowed headers
            copyHeaders(request, requestBuilder, backend);

            // Set method and body
            setMethodAndBody(requestBuilder, method, body);

            HttpRequest upstreamRequest = requestBuilder.build();
            HttpResponse<byte[]> upstreamResponse = httpClient.send(upstreamRequest, HttpResponse.BodyHandlers.ofByteArray());

            long latencyMs = System.currentTimeMillis() - startTime;
            int statusCode = upstreamResponse.statusCode();

            // Extract response headers and enrich with proxy diagnostic telemetry
            HttpHeaders responseHeaders = extractResponseHeaders(upstreamResponse.headers().map());
            responseHeaders.set("X-Proxy-By", "FluxGate");
            responseHeaders.set("X-Upstream-Backend", backend.getUrl());
            responseHeaders.set("X-Proxy-Latency", latencyMs + "ms");

            log.info("[PROXY] Method={} Path={} Query={} Backend={} Status={} Latency={}ms",
                    method, requestUri, (queryString != null ? queryString : "-"), backend.getUrl(), statusCode, latencyMs);

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

    private void copyHeaders(HttpServletRequest request, HttpRequest.Builder builder, BackendServer backend) {
        Enumeration<String> headerNames = request.getHeaderNames();
        if (headerNames != null) {
            while (headerNames.hasMoreElements()) {
                String headerName = headerNames.nextElement();
                if (headerName == null) continue;

                String lower = headerName.toLowerCase();
                if (HOP_BY_HOP_HEADERS.contains(lower)) {
                    continue;
                }

                Enumeration<String> values = request.getHeaders(headerName);
                if (values != null) {
                    while (values.hasMoreElements()) {
                        String value = values.nextElement();
                        if (value != null) {
                            try {
                                builder.header(headerName, value);
                            } catch (IllegalArgumentException ignored) {
                                // Ignore restricted or malformed headers
                            }
                        }
                    }
                }
            }
        }

        // Add standard X-Forwarded-* headers
        String clientIp = request.getHeader("X-Forwarded-For");
        if (clientIp == null || clientIp.isBlank()) {
            clientIp = request.getRemoteAddr();
        } else {
            clientIp = clientIp + ", " + request.getRemoteAddr();
        }
        builder.header("X-Forwarded-For", clientIp);
        builder.header("X-Forwarded-Proto", request.getScheme());
        builder.header("X-Forwarded-Host", request.getHeader("Host") != null ? request.getHeader("Host") : request.getServerName());
        builder.header("X-Forwarded-Port", String.valueOf(request.getServerPort()));
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

    private HttpHeaders extractResponseHeaders(Map<String, List<String>> upstreamHeaders) {
        HttpHeaders headers = new HttpHeaders();
        for (Map.Entry<String, List<String>> entry : upstreamHeaders.entrySet()) {
            String name = entry.getKey();
            if (name == null) continue;

            String lower = name.toLowerCase();
            if (HOP_BY_HOP_HEADERS.contains(lower)) {
                continue;
            }

            for (String val : entry.getValue()) {
                headers.add(name, val);
            }
        }
        return headers;
    }
}
