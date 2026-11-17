package com.fluxgate.strategy;

import com.fluxgate.model.BackendServer;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Least Connections load balancing strategy.
 * Directs traffic to the healthy backend with the lowest number of active (in-flight) requests.
 * Breaks ties using total request count.
 */
@Component
public class LeastConnectionsStrategy implements LoadBalancingStrategy {

    private static final Comparator<BackendServer> BY_LEAST_CONNECTIONS =
            Comparator.comparingLong(BackendServer::getActiveRequests)
                    .thenComparingLong(BackendServer::getTotalRequests);

    @Override
    public Optional<BackendServer> select(List<BackendServer> backends) {
        if (backends == null || backends.isEmpty()) {
            return Optional.empty();
        }

        return backends.stream()
                .filter(BackendServer::isHealthy)
                .min(BY_LEAST_CONNECTIONS);
    }

    @Override
    public String getName() {
        return "LEAST_CONNECTIONS";
    }
}
