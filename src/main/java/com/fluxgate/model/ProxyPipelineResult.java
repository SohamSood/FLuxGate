package com.fluxgate.model;

/**
 * Encapsulates the execution result of the proxy pipeline dispatch,
 * including origin backend routing, in-flight failover details,
 * and SingleFlight coalescing metadata.
 */
public class ProxyPipelineResult {

    private final ProxyResponse response;
    private final String originBackend;
    private final String routeLabel;
    private final String cacheStatus;
    private final boolean coalesced;
    private final int waiterCount;
    private final String circuitBreakerState;
    private final int failoverAttempts;
    private final String failoverOrigin;

    public ProxyPipelineResult(ProxyResponse response,
                               String originBackend,
                               String routeLabel,
                               String cacheStatus,
                               boolean coalesced,
                               int waiterCount,
                               String circuitBreakerState,
                               int failoverAttempts,
                               String failoverOrigin) {
        this.response = response;
        this.originBackend = originBackend;
        this.routeLabel = routeLabel;
        this.cacheStatus = cacheStatus;
        this.coalesced = coalesced;
        this.waiterCount = waiterCount;
        this.circuitBreakerState = circuitBreakerState;
        this.failoverAttempts = failoverAttempts;
        this.failoverOrigin = failoverOrigin;
    }

    public ProxyResponse getResponse() {
        return response;
    }

    public String getOriginBackend() {
        return originBackend;
    }

    public String getRouteLabel() {
        return routeLabel;
    }

    public String getCacheStatus() {
        return cacheStatus;
    }

    public boolean isCoalesced() {
        return coalesced;
    }

    public int getWaiterCount() {
        return waiterCount;
    }

    public String getCircuitBreakerState() {
        return circuitBreakerState;
    }

    public int getFailoverAttempts() {
        return failoverAttempts;
    }

    public String getFailoverOrigin() {
        return failoverOrigin;
    }
}
