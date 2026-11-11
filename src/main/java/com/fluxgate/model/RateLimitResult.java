package com.fluxgate.model;

/**
 * Result of evaluating rate limit constraints on an incoming client request.
 */
public class RateLimitResult {

    private final boolean allowed;
    private final long remainingTokens;
    private final long capacity;
    private final long resetSeconds;
    private final String clientIp;

    public RateLimitResult(boolean allowed, long remainingTokens, long capacity, long resetSeconds, String clientIp) {
        this.allowed = allowed;
        this.remainingTokens = remainingTokens;
        this.capacity = capacity;
        this.resetSeconds = resetSeconds;
        this.clientIp = clientIp;
    }

    public boolean isAllowed() {
        return allowed;
    }

    public long getRemainingTokens() {
        return remainingTokens;
    }

    public long getCapacity() {
        return capacity;
    }

    public long getResetSeconds() {
        return resetSeconds;
    }

    public String getClientIp() {
        return clientIp;
    }
}
