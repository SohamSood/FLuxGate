package com.fluxgate.strategy;

import com.fluxgate.model.BackendServer;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Enterprise Smooth Weighted Round Robin Load Balancing Strategy.
 *
 * Implements NGINX's smooth weighted round robin algorithm to distribute traffic
 * proportionally across heterogeneous backend servers according to their configured weights.
 * Prevents clustering or burstiness on high-weight servers by interleaving requests smoothly.
 */
@Component
public class WeightedRoundRobinStrategy implements LoadBalancingStrategy {

    public static final String NAME = "WEIGHTED_ROUND_ROBIN";

    // Dynamic current weights per backend URL
    private final Map<String, AtomicInteger> currentWeights = new ConcurrentHashMap<>();

    @Override
    public synchronized Optional<BackendServer> select(List<BackendServer> backends) {
        if (backends == null || backends.isEmpty()) {
            return Optional.empty();
        }

        if (backends.size() == 1) {
            return Optional.of(backends.get(0));
        }

        int totalWeight = 0;
        BackendServer bestServer = null;
        int maxCurrentWeight = Integer.MIN_VALUE;

        // 1. Add each server's configured weight to its current weight
        for (BackendServer backend : backends) {
            int weight = Math.max(1, backend.getWeight());
            totalWeight += weight;

            AtomicInteger cw = currentWeights.computeIfAbsent(backend.getUrl(), k -> new AtomicInteger(0));
            int updatedCw = cw.addAndGet(weight);

            if (bestServer == null || updatedCw > maxCurrentWeight) {
                maxCurrentWeight = updatedCw;
                bestServer = backend;
            }
        }

        // 2. Subtract total weight from the chosen server's current weight
        if (bestServer != null) {
            AtomicInteger bestCw = currentWeights.get(bestServer.getUrl());
            if (bestCw != null) {
                bestCw.addAndGet(-totalWeight);
            }
            return Optional.of(bestServer);
        }

        return Optional.of(backends.get(0));
    }

    @Override
    public String getName() {
        return NAME;
    }

    public synchronized void reset() {
        currentWeights.clear();
    }
}
