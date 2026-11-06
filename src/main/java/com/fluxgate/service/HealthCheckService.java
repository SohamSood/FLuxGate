package com.fluxgate.service;

import com.fluxgate.config.FluxGateProperties;
import com.fluxgate.model.BackendServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;

/**
 * Periodically probes backend instances at their /health endpoints and updates health state.
 */
@Service
public class HealthCheckService {

    private static final Logger log = LoggerFactory.getLogger(HealthCheckService.class);

    private final BackendRegistry backendRegistry;
    private final HttpClient httpClient;
    private final FluxGateProperties properties;

    public HealthCheckService(BackendRegistry backendRegistry, HttpClient httpClient, FluxGateProperties properties) {
        this.backendRegistry = backendRegistry;
        this.httpClient = httpClient;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${fluxgate.health-check.interval-ms:10000}", initialDelay = 2000)
    public void runPeriodicHealthChecks() {
        if (!properties.getHealthCheck().isEnabled()) {
            return;
        }

        for (BackendServer backend : backendRegistry.getAllBackends()) {
            checkBackend(backend);
        }
    }

    public boolean checkBackend(BackendServer backend) {
        String healthUrl = backend.getUrl() + properties.getHealthCheck().getHealthPath();
        boolean previousState = backend.isHealthy();

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(healthUrl))
                    .timeout(Duration.ofMillis(properties.getHealthCheck().getTimeoutMs()))
                    .GET()
                    .build();

            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            int code = response.statusCode();

            if (code >= 200 && code < 300) {
                backend.markHealthy();
                if (!previousState) {
                    log.info("[HEALTH RECOVERED] Backend {} is now HEALTHY (status: {})", backend.getUrl(), code);
                }
                return true;
            } else {
                String reason = "HTTP status " + code;
                backend.markUnhealthy(reason);
                if (previousState) {
                    log.warn("[HEALTH FAILED] Backend {} marked UNHEALTHY (status: {})", backend.getUrl(), code);
                }
                return false;
            }
        } catch (HttpTimeoutException te) {
            String reason = "Health check timed out after " + properties.getHealthCheck().getTimeoutMs() + "ms";
            backend.markUnhealthy(reason);
            if (previousState) {
                log.warn("[HEALTH TIMEOUT] Backend {} marked UNHEALTHY: {}", backend.getUrl(), reason);
            }
            return false;
        } catch (Exception ex) {
            String reason = ex.getClass().getSimpleName() + ": " + ex.getMessage();
            backend.markUnhealthy(reason);
            if (previousState) {
                log.warn("[HEALTH UNREACHABLE] Backend {} marked UNHEALTHY: {}", backend.getUrl(), reason);
            }
            return false;
        }
    }
}
