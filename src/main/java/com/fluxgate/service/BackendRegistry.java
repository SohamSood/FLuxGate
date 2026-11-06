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
     * Registers a new backend URL if not already present.
     */
    public synchronized BackendServer registerBackend(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new IllegalArgumentException("Backend URL cannot be blank");
        }
        BackendServer newServer = new BackendServer(rawUrl);
        for (BackendServer existing : backends) {
            if (existing.getUrl().equalsIgnoreCase(newServer.getUrl())) {
                return existing;
            }
        }
        backends.add(newServer);
        log.info("Registered new backend: {}", newServer.getUrl());
        return newServer;
    }

    /**
     * Deregisters a backend by its URL.
     */
    public synchronized boolean deregisterBackend(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            return false;
        }
        String sanitized = rawUrl.trim();
        while (sanitized.endsWith("/")) {
            sanitized = sanitized.substring(0, sanitized.length() - 1);
        }
        for (BackendServer server : backends) {
            if (server.getUrl().equalsIgnoreCase(sanitized)) {
                backends.remove(server);
                log.info("Deregistered backend: {}", sanitized);
                return true;
            }
        }
        return false;
    }

    public List<BackendServer> getAllBackends() {
        return Collections.unmodifiableList(backends);
    }

    public List<BackendServer> getHealthyBackends() {
        return backends.stream().filter(BackendServer::isHealthy).toList();
    }

    public Optional<BackendServer> findByUrl(String url) {
        if (url == null) return Optional.empty();
        String sanitized = url.trim();
        while (sanitized.endsWith("/")) {
            sanitized = sanitized.substring(0, sanitized.length() - 1);
        }
        for (BackendServer server : backends) {
            if (server.getUrl().equalsIgnoreCase(sanitized)) {
                return Optional.of(server);
            }
        }
        return Optional.empty();
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
