package com.fluxgate.strategy;

import com.fluxgate.model.BackendServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Primary strategy orchestrator delegating to the currently active strategy.
 * Supports dynamic switching between strategies at runtime.
 */
@Component
@Primary
public class LoadBalancingStrategyManager implements LoadBalancingStrategy {

    private static final Logger log = LoggerFactory.getLogger(LoadBalancingStrategyManager.class);

    private final Map<String, LoadBalancingStrategy> strategies = new ConcurrentHashMap<>();
    private volatile LoadBalancingStrategy activeStrategy;

    public LoadBalancingStrategyManager(List<LoadBalancingStrategy> availableStrategies,
                                        RoundRobinStrategy defaultStrategy) {
        for (LoadBalancingStrategy strategy : availableStrategies) {
            // Avoid self-registration
            if (strategy != this) {
                strategies.put(strategy.getName().toUpperCase(), strategy);
            }
        }
        this.activeStrategy = defaultStrategy;
        log.info("Initialized LoadBalancingStrategyManager with strategies: {}, default: {}",
                strategies.keySet(), defaultStrategy.getName());
    }

    @Override
    public Optional<BackendServer> select(List<BackendServer> backends) {
        return activeStrategy.select(backends);
    }

    @Override
    public String getName() {
        return activeStrategy.getName();
    }

    public String getActiveStrategyName() {
        return activeStrategy.getName();
    }

    public List<String> getAvailableStrategies() {
        return new ArrayList<>(strategies.keySet());
    }

    public synchronized void setActiveStrategy(String strategyName) {
        if (strategyName == null || strategyName.isBlank()) {
            throw new IllegalArgumentException("Strategy name cannot be blank");
        }
        String key = strategyName.trim().toUpperCase();
        LoadBalancingStrategy found = strategies.get(key);
        if (found == null) {
            throw new IllegalArgumentException("Unknown strategy: '" + strategyName + "'. Available: " + strategies.keySet());
        }
        this.activeStrategy = found;
        log.info("[STRATEGY SWITCHED] Active strategy changed to: {}", key);
    }
}

