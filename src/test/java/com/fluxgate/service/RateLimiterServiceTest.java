package com.fluxgate.service;

import com.fluxgate.config.FluxGateProperties;
import com.fluxgate.model.RateLimitResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RateLimiterServiceTest {

    private FluxGateProperties properties;
    private RateLimiterService rateLimiterService;

    @BeforeEach
    void setUp() {
        properties = new FluxGateProperties();
        properties.getRateLimit().setEnabled(true);
        properties.getRateLimit().setCapacity(3);
        properties.getRateLimit().setRefillRatePerMinute(60);
        rateLimiterService = new RateLimiterService(properties);
    }

    @Test
    void testTokenConsumptionAndThrottling() {
        String clientIp = "192.168.1.100";

        // First 3 requests should be allowed
        RateLimitResult r1 = rateLimiterService.tryConsume(clientIp);
        assertTrue(r1.isAllowed());
        assertEquals(2, r1.getRemainingTokens());

        RateLimitResult r2 = rateLimiterService.tryConsume(clientIp);
        assertTrue(r2.isAllowed());
        assertEquals(1, r2.getRemainingTokens());

        RateLimitResult r3 = rateLimiterService.tryConsume(clientIp);
        assertTrue(r3.isAllowed());
        assertEquals(0, r3.getRemainingTokens());

        // 4th request must be throttled (429)
        RateLimitResult r4 = rateLimiterService.tryConsume(clientIp);
        assertFalse(r4.isAllowed());
        assertEquals(0, r4.getRemainingTokens());
        assertTrue(r4.getResetSeconds() >= 1);
        assertEquals(1, rateLimiterService.getTotalBlockedRequests());
    }

    @Test
    void testIndependentBucketsPerIp() {
        String ip1 = "10.0.0.1";
        String ip2 = "10.0.0.2";

        // Exhaust IP1's bucket
        rateLimiterService.tryConsume(ip1);
        rateLimiterService.tryConsume(ip1);
        rateLimiterService.tryConsume(ip1);
        assertFalse(rateLimiterService.tryConsume(ip1).isAllowed());

        // IP2 should still have full capacity
        RateLimitResult r2 = rateLimiterService.tryConsume(ip2);
        assertTrue(r2.isAllowed());
        assertEquals(2, r2.getRemainingTokens());
        assertEquals(2, rateLimiterService.getTrackedIpCount());
    }

    @Test
    void testDisabledRateLimiterAllowsAll() {
        rateLimiterService.setEnabled(false);

        for (int i = 0; i < 10; i++) {
            RateLimitResult res = rateLimiterService.tryConsume("1.1.1.1");
            assertTrue(res.isAllowed());
        }
        assertEquals(0, rateLimiterService.getTotalBlockedRequests());
    }
}

