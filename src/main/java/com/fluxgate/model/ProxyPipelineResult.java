package com.fluxgate.model;

/**
 * Encapsulates the execution result of the proxy pipeline dispatch,
 * including origin backend routing, in-flight failover details,
 * and SingleFlight coalescing metadata.
 */
public record ProxyPipelineResult(
        ProxyResponse response,
        String originBackend,
        String routeLabel,
        String cacheStatus,
        boolean coalesced,
        int waiterCount,
        String circuitBreakerState,
        int failoverAttempts,
        String failoverOrigin
) {
    public ProxyResponse getResponse() { return response; }
    public String getOriginBackend() { return originBackend; }
    public String getRouteLabel() { return routeLabel; }
    public String getCacheStatus() { return cacheStatus; }
    public boolean isCoalesced() { return coalesced; }
    public int getWaiterCount() { return waiterCount; }
    public String getCircuitBreakerState() { return circuitBreakerState; }
    public int getFailoverAttempts() { return failoverAttempts; }
    public String getFailoverOrigin() { return failoverOrigin; }
}
