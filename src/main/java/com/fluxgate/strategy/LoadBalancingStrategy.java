package com.fluxgate.strategy;

import com.fluxgate.model.BackendServer;

import java.util.List;
import java.util.Optional;

/**
 * Strategy interface for selecting an upstream backend server.
 */
public interface LoadBalancingStrategy {

    /**
     * Selects a backend server from the provided list of candidates.
     *
     * @param backends list of registered backends
     * @return Optional containing the chosen backend, or empty if none are available/healthy
     */
    Optional<BackendServer> select(List<BackendServer> backends);

    /**
     * Identifier for the strategy.
     */
    String getName();
}
