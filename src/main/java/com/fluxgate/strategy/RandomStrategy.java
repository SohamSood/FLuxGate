package com.fluxgate.strategy;

import com.fluxgate.model.BackendServer;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Random load balancing strategy.
 * Selects uniformly at random among healthy backends.
 */
@Component
public class RandomStrategy implements LoadBalancingStrategy {

    @Override
    public Optional<BackendServer> select(List<BackendServer> backends) {
        if (backends == null || backends.isEmpty()) {
            return Optional.empty();
        }

        List<BackendServer> healthy = backends.stream()
                .filter(BackendServer::isHealthy)
                .toList();

        if (healthy.isEmpty()) {
            return Optional.empty();
        }

        int index = ThreadLocalRandom.current().nextInt(healthy.size());
        return Optional.of(healthy.get(index));
    }

    @Override
    public String getName() {
        return "RANDOM";
    }
}

