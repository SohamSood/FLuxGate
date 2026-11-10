package com.fluxgate.strategy;

import com.fluxgate.model.BackendServer;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Least Connections load balancing strategy.
 * Directs traffic to the healthy backend with the lowest number of active (in-flight) requests.
 * Breaks ties using total request count, then list order.
 */
@Component
public class LeastConnectionsStrategy implements LoadBalancingStrategy {

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

        BackendServer leastLoaded = healthy.get(0);
        for (int i = 1; i < healthy.size(); i++) {
            BackendServer candidate = healthy.get(i);
            long candidateActive = candidate.getActiveRequests();
            long currentMin = leastLoaded.getActiveRequests();

            if (candidateActive < currentMin) {
                leastLoaded = candidate;
            } else if (candidateActive == currentMin) {
                // Tie-breaker: lowest total requests served
                if (candidate.getTotalRequests() < leastLoaded.getTotalRequests()) {
                    leastLoaded = candidate;
                }
            }
        }

        return Optional.of(leastLoaded);
    }

    @Override
    public String getName() {
        return "LEAST_CONNECTIONS";
    }
}

