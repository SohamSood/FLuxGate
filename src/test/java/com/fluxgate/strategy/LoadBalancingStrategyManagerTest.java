package com.fluxgate.strategy;

import com.fluxgate.model.BackendServer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LoadBalancingStrategyManagerTest {

    @Test
    @DisplayName("Should switch strategies dynamically and delegate selection correctly")
    void testStrategySwitching() {
        RoundRobinStrategy roundRobin = new RoundRobinStrategy();
        LeastConnectionsStrategy leastConnections = new LeastConnectionsStrategy();
        RandomStrategy random = new RandomStrategy();

        LoadBalancingStrategyManager manager = new LoadBalancingStrategyManager(
                List.of(roundRobin, leastConnections, random),
                roundRobin
        );

        assertEquals("ROUND_ROBIN", manager.getActiveStrategyName());

        manager.setActiveStrategy("LEAST_CONNECTIONS");
        assertEquals("LEAST_CONNECTIONS", manager.getActiveStrategyName());

        manager.setActiveStrategy("RANDOM");
        assertEquals("RANDOM", manager.getActiveStrategyName());

        assertThrows(IllegalArgumentException.class, () -> manager.setActiveStrategy("NON_EXISTENT"));
    }
}

