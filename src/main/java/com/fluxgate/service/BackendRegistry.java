package com.fluxgate.service;

import com.fluxgate.config.FluxGateProperties;
import com.fluxgate.model.BackendServer;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Registry holding all configured backend instances and tracking their live health state.
 */
@Service
public class BackendRegistry {

    private static final Logger log = LoggerFactory.getLogger(BackendRegistry.class);

    private final FluxGateProperties properties;
    private final CopyOnWriteArrayList<BackendServer> backends = new CopyOnWriteArrayList<>();

    public BackendRegistry(FluxGateProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void init() {
        if (properties.getBackends() != null) {
            for (String url : properties.getBackends()) {
                registerBackend(url);
            }
        }
        log.info("Initialized BackendRegistry with {} backends: {}", backends.size(), backends);
    }

    /**
     * Registers a new backend URL if not already present, with default weight 1.
     */
    public synchronized BackendServer registerBackend(String rawUrl) {
        return registerBackend(rawUrl, 1);
    }

    /**
     * Registers a new backend URL with configured weight if not already present.
     */
    public synchronized BackendServer registerBackend(String rawUrl, int weight) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new IllegalArgumentException("Backend URL cannot be blank");
        }
        String cleanUrl = BackendServer.sanitizeUrl(rawUrl);
        for (BackendServer existing : backends) {
            if (existing.getUrl().equalsIgnoreCase(cleanUrl)) {
                existing.setWeight(weight);
                return existing;
            }
        }
        BackendServer newServer = new BackendServer(cleanUrl, true, weight);
        backends.add(newServer);
        log.info("Registered new backend: {} (weight={})", newServer.getUrl(), newServer.getWeight());
        return newServer;
    }

    /**
     * Updates the weight of an existing registered backend.
     */
    public synchronized boolean setBackendWeight(String rawUrl, int weight) {
        Optional<BackendServer> opt = findByUrl(rawUrl);
        if (opt.isPresent()) {
            opt.get().setWeight(weight);
            log.info("Updated weight for backend {} to {}", rawUrl, weight);
            return true;
        }
        return false;
    }

    /**
     * Deregisters a backend by its URL.
     */
    public synchronized boolean deregisterBackend(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            return false;
        }
        String sanitized = BackendServer.sanitizeUrl(rawUrl);
        boolean removed = backends.removeIf(server -> server.getUrl().equalsIgnoreCase(sanitized));
        if (removed) {
            log.info("Deregistered backend: {}", sanitized);
        }
        return removed;
    }

    public List<BackendServer> getAllBackends() {
        return Collections.unmodifiableList(backends);
    }

    public List<BackendServer> getHealthyBackends() {
        return backends.stream().filter(BackendServer::isHealthy).toList();
    }

    public Optional<BackendServer> findByUrl(String url) {
        if (url == null || url.isBlank()) return Optional.empty();
        String sanitized = BackendServer.sanitizeUrl(url);
        return backends.stream()
                .filter(server -> server.getUrl().equalsIgnoreCase(sanitized))
                .findFirst();
    }

    public int size() {
        return backends.size();
    }

    /**
     * Clears all registered backends (useful for test isolation).
     */
    public synchronized void clear() {
        backends.clear();
        log.info("Cleared all backends from registry");
    }
}
