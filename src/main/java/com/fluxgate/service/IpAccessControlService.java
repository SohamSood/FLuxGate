package com.fluxgate.service;

import com.fluxgate.config.FluxGateProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * High-performance IP access control and firewall service.
 * Enforces dynamic IP blocklisting and optional allowlisting to shield upstream backends.
 */
@Service
public class IpAccessControlService {

    private static final Logger log = LoggerFactory.getLogger(IpAccessControlService.class);

    private final AtomicBoolean enabled = new AtomicBoolean(true);
    private final Set<String> blockedIps = ConcurrentHashMap.newKeySet();
    private final Set<String> allowedIps = ConcurrentHashMap.newKeySet();

    public IpAccessControlService(FluxGateProperties properties) {
        if (properties.getSecurity() != null) {
            this.enabled.set(properties.getSecurity().isEnabled());
            if (properties.getSecurity().getBlockedIps() != null) {
                this.blockedIps.addAll(properties.getSecurity().getBlockedIps());
            }
            if (properties.getSecurity().getAllowedIps() != null) {
                this.allowedIps.addAll(properties.getSecurity().getAllowedIps());
            }
        }
        log.info("[FIREWALL] Initialized IP Access Control: enabled={}, blockedCount={}, allowedCount={}",
                enabled.get(), blockedIps.size(), allowedIps.size());
    }

    /**
     * Checks if a client IP is permitted to access the gateway.
     */
    public boolean isAllowed(String clientIp) {
        if (!enabled.get() || clientIp == null) {
            return true;
        }

        String ip = clientIp.trim();

        // 1. Explicitly blocked IP check
        if (blockedIps.contains(ip)) {
            return false;
        }

        // 2. Allowlist check (if allowlist configured, IP must be present)
        if (!allowedIps.isEmpty() && !allowedIps.contains(ip)) {
            return false;
        }

        return true;
    }

    public boolean blockIp(String ip) {
        if (ip == null || ip.isBlank()) return false;
        boolean added = blockedIps.add(ip.trim());
        if (added) {
            log.warn("[FIREWALL] Blocked client IP: {}", ip.trim());
        }
        return added;
    }

    public boolean unblockIp(String ip) {
        if (ip == null || ip.isBlank()) return false;
        boolean removed = blockedIps.remove(ip.trim());
        if (removed) {
            log.info("[FIREWALL] Unblocked client IP: {}", ip.trim());
        }
        return removed;
    }

    public void allowIp(String ip) {
        if (ip != null && !ip.isBlank()) {
            allowedIps.add(ip.trim());
        }
    }

    public Set<String> getBlockedIps() {
        return Collections.unmodifiableSet(blockedIps);
    }

    public Set<String> getAllowedIps() {
        return Collections.unmodifiableSet(allowedIps);
    }

    public boolean isEnabled() {
        return enabled.get();
    }

    public void setEnabled(boolean enabled) {
        this.enabled.set(enabled);
        log.info("[FIREWALL] IP Access Control enabled set to: {}", enabled);
    }

    public void clear() {
        blockedIps.clear();
        allowedIps.clear();
    }
}
