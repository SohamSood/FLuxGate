package com.fluxgate.service;

import com.fluxgate.model.BackendServer;
import com.fluxgate.model.ProxyPipelineResult;
import com.fluxgate.model.ProxyResponse;
import com.fluxgate.resilience.CircuitBreakerManager;
import com.fluxgate.strategy.LoadBalancingStrategy;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Core orchestrator for reverse proxy dispatching:
 * executes load balancing strategy, circuit breaker checks, transparent in-flight failover,
 * SingleFlight request coalescing, and edge cache lifecycle.
 */
@Service
public class ProxyExecutionPipeline {

    private static final Logger log = LoggerFactory.getLogger(ProxyExecutionPipeline.class);

    private final BackendRegistry backendRegistry;
    private final LoadBalancingStrategy loadBalancingStrategy;
    private final ProxyService proxyService;
    private final CircuitBreakerManager circuitBreakerManager;
    private final ResponseCacheService responseCacheService;
    private final RequestCoalescingService requestCoalescingService;

    public ProxyExecutionPipeline(BackendRegistry backendRegistry,
                                  LoadBalancingStrategy loadBalancingStrategy,
                                  ProxyService proxyService,
                                  CircuitBreakerManager circuitBreakerManager,
                                  ResponseCacheService responseCacheService,
                                  RequestCoalescingService requestCoalescingService) {
        this.backendRegistry = backendRegistry;
        this.loadBalancingStrategy = loadBalancingStrategy;
        this.proxyService = proxyService;
        this.circuitBreakerManager = circuitBreakerManager;
        this.responseCacheService = responseCacheService;
        this.requestCoalescingService = requestCoalescingService;
    }

    /**
     * Dispatches an incoming request through coalescing (if idempotent) or direct execution,
     * managing failovers, circuit state, and edge cache.
     */
    public ProxyPipelineResult dispatch(HttpServletRequest request,
                                       byte[] body,
                                       String cacheKey,
                                       boolean isCacheableMethod,
                                       boolean cacheBypassRequested) {

        RequestCoalescingService.CoalescedResult coalescedResult;

        if (isCacheableMethod) {
            coalescedResult = requestCoalescingService.execute(cacheKey,
                    () -> executeWithFailover(request, body, cacheKey, true));
        } else {
            // State mutation: invalidate cache and dispatch directly without coalescing
            responseCacheService.invalidatePath(request.getRequestURI());
            RequestCoalescingService.CoalescedExecutionResult exec =
                    executeWithFailover(request, body, cacheKey, false);
            coalescedResult = new RequestCoalescingService.CoalescedResult(
                    exec.getResponse(), false, 0, exec.getOriginBackend(),
                    exec.getFailoverAttempts(), exec.getFailoverOrigin()
            );
        }

        boolean wasCoalesced = coalescedResult.isCoalesced();
        int waiterCount = coalescedResult.getWaiterCount();

        // Determine cache & coalescing status
        String cacheStatus;
        if (wasCoalesced) {
            cacheStatus = "COALESCED";
        } else if (isCacheableMethod) {
            cacheStatus = cacheBypassRequested || !responseCacheService.isEnabled() ? "BYPASS" : "MISS";
        } else {
            cacheStatus = "BYPASS";
        }

        String routeLabel = coalescedResult.getOriginBackend();
        if (wasCoalesced) {
            routeLabel += " (SingleFlight Coalesced)";
        } else if (coalescedResult.getFailoverAttempts() > 0) {
            routeLabel += " (Failover from " + coalescedResult.getFailoverOrigin() + ")";
        }

        String circuitBreakerState = circuitBreakerManager
                .getCircuitBreaker(coalescedResult.getOriginBackend())
                .getState()
                .name();

        return new ProxyPipelineResult(
                coalescedResult.getResponse(),
                coalescedResult.getOriginBackend(),
                routeLabel,
                cacheStatus,
                wasCoalesced,
                waiterCount,
                circuitBreakerState,
                coalescedResult.getFailoverAttempts(),
                coalescedResult.getFailoverOrigin()
        );
    }

    /**
     * Executes backend forwarding with automatic in-flight failover protection on upstream failures.
     */
    private RequestCoalescingService.CoalescedExecutionResult executeWithFailover(
            HttpServletRequest request, byte[] body, String cacheKey, boolean isCacheableMethod) {

        BackendServer backend = selectInitialBackend(request.getRequestURI());

        // Check if circuit breaker allows request to primary backend
        if (!circuitBreakerManager.allowRequest(backend.getUrl())) {
            Optional<BackendServer> alternateOpt = selectFailoverBackend(backend);
            if (alternateOpt.isPresent()) {
                backend = alternateOpt.get();
                circuitBreakerManager.allowRequest(backend.getUrl());
            }
        }

        ProxyResponse proxyResponse;
        BackendServer activeBackend = backend;
        int failoverAttempts = 0;
        String failoverOrigin = null;

        try {
            proxyResponse = proxyService.forward(request, body, activeBackend);

            // Upstream 5xx: record failure and attempt failover
            if (proxyResponse.getStatusCode() >= 500) {
                String failureReason = "HTTP " + proxyResponse.getStatusCode() + " upstream error";
                circuitBreakerManager.recordFailure(activeBackend.getUrl(), failureReason);

                Optional<BackendServer> failoverOpt = selectFailoverBackend(activeBackend);
                if (failoverOpt.isPresent()) {
                    BackendServer alternate = failoverOpt.get();
                    failoverOrigin = activeBackend.getUrl();
                    activeBackend = alternate;
                    failoverAttempts++;
                    circuitBreakerManager.recordFailover(failoverOrigin);
                    log.warn("[IN-FLIGHT FAILOVER] Backend {} returned 5xx. Transparently rerouting request to alternate backend {}",
                            failoverOrigin, activeBackend.getUrl());
                    proxyResponse = proxyService.forward(request, body, activeBackend);
                }
            }
        } catch (BadGatewayException | GatewayTimeoutException ex) {
            String failureReason = ex.getMessage();
            circuitBreakerManager.recordFailure(activeBackend.getUrl(), failureReason);

            Optional<BackendServer> failoverOpt = selectFailoverBackend(activeBackend);
            if (failoverOpt.isPresent()) {
                BackendServer alternate = failoverOpt.get();
                failoverOrigin = activeBackend.getUrl();
                activeBackend = alternate;
                failoverAttempts++;
                circuitBreakerManager.recordFailover(failoverOrigin);
                log.warn("[IN-FLIGHT FAILOVER] Backend {} failed ({}). Transparently rerouting request to alternate backend {}",
                        failoverOrigin, failureReason, activeBackend.getUrl());
                proxyResponse = proxyService.forward(request, body, activeBackend);
            } else {
                throw ex;
            }
        }

        if (proxyResponse.getStatusCode() < 500) {
            circuitBreakerManager.recordSuccess(activeBackend.getUrl());
        }

        // Cache successful idempotent responses
        if (isCacheableMethod && responseCacheService.isEnabled()
                && proxyResponse.getStatusCode() >= 200 && proxyResponse.getStatusCode() < 300) {
            responseCacheService.put(
                    cacheKey,
                    proxyResponse.getStatusCode(),
                    proxyResponse.getHeaders(),
                    proxyResponse.getBody(),
                    activeBackend.getUrl()
            );
        }

        return new RequestCoalescingService.CoalescedExecutionResult(
                proxyResponse,
                activeBackend.getUrl(),
                failoverAttempts,
                failoverOrigin
        );
    }

    private BackendServer selectInitialBackend(String requestUri) {
        List<BackendServer> permittedBackends = backendRegistry.getHealthyBackends().stream()
                .filter(b -> circuitBreakerManager.isRequestPermitted(b.getUrl()))
                .toList();

        List<BackendServer> selectionPool = !permittedBackends.isEmpty()
                ? permittedBackends
                : backendRegistry.getAllBackends();

        return loadBalancingStrategy.select(selectionPool)
                .orElseThrow(() -> new NoHealthyBackendException(
                        "No healthy backend instances available to handle the request: " + requestUri));
    }

    private Optional<BackendServer> selectFailoverBackend(BackendServer failedBackend) {
        List<BackendServer> candidates = backendRegistry.getHealthyBackends().stream()
                .filter(b -> !b.getUrl().equalsIgnoreCase(failedBackend.getUrl()))
                .filter(b -> circuitBreakerManager.isRequestPermitted(b.getUrl()))
                .toList();
        if (!candidates.isEmpty()) {
            return loadBalancingStrategy.select(candidates);
        }
        return Optional.empty();
    }
}
