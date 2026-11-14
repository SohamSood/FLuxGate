package com.fluxgate.service;

import com.fluxgate.config.FluxGateProperties;
import com.fluxgate.model.CachedResponse;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * High-performance, thread-safe in-memory edge response caching service.
 * Delivers sub-millisecond responses for read requests, reduces backend load,
 * and tracks hit/miss telemetry.
 */
@Service
public class ResponseCacheService {

    private final ConcurrentHashMap<String, CachedResponse> cache = new ConcurrentHashMap<>();
    private final AtomicLong hitCount = new AtomicLong(0);
    private final AtomicLong missCount = new AtomicLong(0);
    private final AtomicLong purgeCount = new AtomicLong(0);

    private volatile boolean enabled;
    private volatile int defaultTtlSeconds;
    private volatile int maxEntries;

    public ResponseCacheService(FluxGateProperties properties) {
        FluxGateProperties.Cache cacheProps = properties.getCache();
        this.enabled = cacheProps != null ? cacheProps.isEnabled() : true;
        this.defaultTtlSeconds = cacheProps != null ? cacheProps.getDefaultTtlSeconds() : 30;
        this.maxEntries = cacheProps != null ? cacheProps.getMaxEntries() : 500;
    }

    public Optional<CachedResponse> get(String key) {
        if (!enabled || key == null) {
            return Optional.empty();
        }

        CachedResponse entry = cache.get(key);
        if (entry == null) {
            missCount.incrementAndGet();
            return Optional.empty();
        }

        if (entry.isExpired()) {
            cache.remove(key);
            missCount.incrementAndGet();
            return Optional.empty();
        }

        hitCount.incrementAndGet();
        return Optional.of(entry);
    }

    public void put(String key, int statusCode, org.springframework.http.HttpHeaders headers,
                    byte[] body, String originBackend) {
        put(key, statusCode, headers, body, originBackend, defaultTtlSeconds);
    }

    public void put(String key, int statusCode, org.springframework.http.HttpHeaders headers,
                    byte[] body, String originBackend, int ttlSeconds) {
        if (!enabled || key == null) {
            return;
        }

        // Only cache successful idempotent responses (200, 203, 301, 302, 304, 404)
        if (statusCode != 200 && statusCode != 203 && statusCode != 204 && statusCode != 301 && statusCode != 304) {
            return;
        }

        // Capacity protection: if size exceeds maxEntries, evict expired or first found
        if (cache.size() >= maxEntries) {
            evictExcess();
        }

        int finalTtl = ttlSeconds > 0 ? ttlSeconds : defaultTtlSeconds;
        cache.put(key, new CachedResponse(statusCode, headers, body, originBackend, finalTtl));
    }

    public boolean invalidate(String key) {
        if (key == null) return false;
        boolean removed = cache.remove(key) != null;
        if (removed) {
            purgeCount.incrementAndGet();
        }
        return removed;
    }

    public int invalidatePath(String path) {
        if (path == null || path.isBlank()) return 0;
        int removedCount = 0;
        for (String key : cache.keySet()) {
            if (key.contains(path)) {
                if (cache.remove(key) != null) {
                    removedCount++;
                }
            }
        }
        if (removedCount > 0) {
            purgeCount.addAndGet(removedCount);
        }
        return removedCount;
    }

    public void clear() {
        int count = cache.size();
        cache.clear();
        purgeCount.addAndGet(count);
    }

    private void evictExcess() {
        // First pass: remove expired items
        Iterator<Map.Entry<String, CachedResponse>> it = cache.entrySet().iterator();
        while (it.hasNext() && cache.size() >= maxEntries) {
            Map.Entry<String, CachedResponse> entry = it.next();
            if (entry.getValue().isExpired()) {
                it.remove();
            }
        }
        // Second pass: if still at capacity, evict any 10%
        if (cache.size() >= maxEntries) {
            int toRemove = Math.max(1, maxEntries / 10);
            Iterator<String> keys = cache.keySet().iterator();
            while (keys.hasNext() && toRemove > 0) {
                keys.next();
                keys.remove();
                toRemove--;
            }
        }
    }

    public Map<String, Object> getStats() {
        long hits = hitCount.get();
        long misses = missCount.get();
        long total = hits + misses;
        double hitRatio = total == 0 ? 0.0 : Math.round(((double) hits / total) * 1000.0) / 10.0;

        List<Map<String, Object>> entriesSummary = new ArrayList<>();
        cache.forEach((key, val) -> {
            if (!val.isExpired() && entriesSummary.size() < 20) {
                entriesSummary.add(Map.of(
                        "key", key,
                        "originBackend", val.getOriginBackend(),
                        "ageSeconds", val.getAgeSeconds(),
                        "remainingTtlSeconds", val.getRemainingTtlSeconds(),
                        "bytes", val.getBody().length
                ));
            }
        });

        Map<String, Object> stats = new HashMap<>();
        stats.put("enabled", enabled);
        stats.put("defaultTtlSeconds", defaultTtlSeconds);
        stats.put("maxEntries", maxEntries);
        stats.put("entriesCount", cache.size());
        stats.put("hitCount", hits);
        stats.put("missCount", misses);
        stats.put("purgeCount", purgeCount.get());
        stats.put("hitRatioPercent", hitRatio);
        stats.put("cachedEntries", entriesSummary);
        return stats;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getDefaultTtlSeconds() {
        return defaultTtlSeconds;
    }

    public void setDefaultTtlSeconds(int defaultTtlSeconds) {
        this.defaultTtlSeconds = Math.max(1, defaultTtlSeconds);
    }

    public int getMaxEntries() {
        return maxEntries;
    }

    public void setMaxEntries(int maxEntries) {
        this.maxEntries = Math.max(10, maxEntries);
    }

    public long getHitCount() {
        return hitCount.get();
    }

    public long getMissCount() {
        return missCount.get();
    }

    public int getEntriesCount() {
        return cache.size();
    }
}

