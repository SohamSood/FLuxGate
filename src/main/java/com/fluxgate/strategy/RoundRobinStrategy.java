package com.fluxgate.strategy;

import com.fluxgate.model.BackendServer;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Thread-safe Round Robin load balancing strategy.
 * Distributes requests sequentially across healthy backend instances.
 */
@Component
public class RoundRobinStrategy implements LoadBalancingStrategy {

    private final AtomicInteger counter = new AtomicInteger(0);

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

        int index = (counter.getAndIncrement() & 0x7FFFFFFF) % healthy.size();
        return Optional.of(healthy.get(index));
    }

    @Override
    public String getName() {
        return "ROUND_ROBIN";
    }

    /**
     * Resets internal counter (useful for unit tests).
     */
    public void reset() {
        counter.set(0);
    }
}
