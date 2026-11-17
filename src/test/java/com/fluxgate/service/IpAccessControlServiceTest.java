package com.fluxgate.service;

import com.fluxgate.config.FluxGateProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class IpAccessControlServiceTest {

    private IpAccessControlService service;
    private FluxGateProperties properties;

    @BeforeEach
    void setUp() {
        properties = new FluxGateProperties();
        properties.getSecurity().setEnabled(true);
        properties.getSecurity().setBlockedIps(List.of("192.168.1.99", "10.0.0.99"));
        service = new IpAccessControlService(properties);
    }

    @Test
    @DisplayName("Should block configured blocked IPs and allow others")
    void testBlockedIps() {
        assertFalse(service.isAllowed("192.168.1.99"));
        assertFalse(service.isAllowed("10.0.0.99"));
        assertTrue(service.isAllowed("192.168.1.1"));
        assertTrue(service.isAllowed("127.0.0.1"));
    }

    @Test
    @DisplayName("Should dynamically block and unblock IPs at runtime")
    void testDynamicBlockAndUnblock() {
        String testIp = "203.0.113.50";
        assertTrue(service.isAllowed(testIp));

        assertTrue(service.blockIp(testIp));
        assertFalse(service.isAllowed(testIp));

        assertTrue(service.unblockIp(testIp));
        assertTrue(service.isAllowed(testIp));
    }

    @Test
    @DisplayName("Should allow all traffic when firewall is disabled")
    void testDisabledFirewall() {
        service.setEnabled(false);
        assertTrue(service.isAllowed("192.168.1.99"));
        assertTrue(service.isAllowed("10.0.0.99"));
    }

    @Test
    @DisplayName("Should enforce allowlist when configured")
    void testAllowlistEnforcement() {
        service.clear();
        service.allowIp("10.0.0.1");

        assertTrue(service.isAllowed("10.0.0.1"));
        assertFalse(service.isAllowed("10.0.0.2"));
        assertFalse(service.isAllowed("192.168.1.1"));
    }
}
