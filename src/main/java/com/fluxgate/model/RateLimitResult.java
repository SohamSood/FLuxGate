package com.fluxgate.model;

/**
 * Result of evaluating rate limit constraints on an incoming client request.
 */
public record RateLimitResult(
        boolean allowed,
        long remainingTokens,
        long capacity,
        long resetSeconds,
        String clientIp
) {
    public boolean isAllowed() { return allowed; }
    public long getRemainingTokens() { return remainingTokens; }
    public long getCapacity() { return capacity; }
    public long getResetSeconds() { return resetSeconds; }
    public String getClientIp() { return clientIp; }
}
