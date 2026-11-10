package com.fluxgate.service;

import com.fluxgate.config.FluxGateProperties;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

/**
 * Automatically spins up lightweight embedded HTTP demo backend servers on ports
 * 8081, 8082, 8083 when localhost backends are configured.
 * This guarantees FluxGate works out-of-the-box in single-container environments (like Render).
 */
@Service
public class EmbeddedBackendService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddedBackendService.class);

    private final FluxGateProperties properties;
    private final List<HttpServer> activeServers = new ArrayList<>();

    public EmbeddedBackendService(FluxGateProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void startIfEnabled() {
        if (!properties.getDemoBackends().isEnabled()) {
            log.info("Embedded demo backends disabled by configuration.");
            return;
        }

        List<String> backends = properties.getBackends();
        boolean hasLocalhost = backends != null && backends.stream().anyMatch(b ->
                b.contains("localhost:") || b.contains("127.0.0.1:") || b.contains("0.0.0.0:"));

        if (!hasLocalhost) {
            log.info("External backends configured. Skipping embedded demo backend servers.");
            return;
        }

        int[] defaultPorts = {8081, 8082, 8083};
        for (int i = 0; i < defaultPorts.length; i++) {
            int port = defaultPorts[i];
            String serverName = "demo-backend-" + (i + 1);

            boolean isConfigured = backends.stream().anyMatch(b -> b.contains(":" + port));
            if (isConfigured) {
                tryStartServer(port, serverName);
            }
        }
    }

    private void tryStartServer(int port, String serverName) {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
            server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());

            // Probe endpoint
            server.createContext("/health", exchange -> {
                String response = String.format(
                        "{\"status\":\"UP\",\"server\":\"%s\",\"port\":%d,\"timestamp\":\"%s\"}",
                        serverName, port, Instant.now()
                );
                sendJsonResponse(exchange, 200, response);
            });

            // Echo / application endpoint
            server.createContext("/", exchange -> {
                String path = exchange.getRequestURI().getPath();
                String query = exchange.getRequestURI().getQuery();
                String method = exchange.getRequestMethod();

                String response = String.format(
                        "{\"status\":\"OK\",\"service\":\"FluxGate Demo Upstream\",\"server\":\"%s\",\"port\":%d,\"method\":\"%s\",\"path\":\"%s\",\"query\":%s,\"timestamp\":\"%s\"}",
                        serverName, port, method, path,
                        (query != null ? "\"" + query + "\"" : "null"),
                        Instant.now()
                );
                sendJsonResponse(exchange, 200, response);
            });

            server.start();
            activeServers.add(server);
            log.info("[EMBEDDED BACKEND] Started {} listening on port {}", serverName, port);

        } catch (IOException e) {
            log.warn("[EMBEDDED BACKEND] Could not bind port {}: {}. Skipping embedded backend.", port, e.getMessage());
        }
    }

    private void sendJsonResponse(HttpExchange exchange, int statusCode, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    @PreDestroy
    public void stopServers() {
        for (HttpServer server : activeServers) {
            try {
                server.stop(0);
            } catch (Exception ignored) {
            }
        }
        activeServers.clear();
        log.info("Stopped all embedded demo backend servers.");
    }
}
