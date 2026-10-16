package com.neha.commercehub.scaling;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * Cache-aside for the public catalogue (product lists, product pages, categories).
 *
 * Most shop traffic is people browsing, and the catalogue changes rarely, so these reads
 * are served from Redis instead of PostgreSQL.
 *
 * Invalidation uses a version number: every key contains the current catalogue version,
 * and any admin change just increments it. All old entries stop being used at once
 * (one Redis command, no scanning for keys) and expire on their own.
 *
 * Entries also expire after a few seconds, so the stock number shown on a product page
 * can lag slightly behind. That is fine: the exact check happens at checkout.
 */
@Component
public class CatalogCache {

    private static final String VERSION_KEY = "catalog:version";

    private final RedisSupport redis;
    private final ObjectMapper objectMapper;
    private final Duration ttl;
    private final Counter hits;
    private final Counter misses;

    public CatalogCache(RedisSupport redis,
                        ObjectMapper objectMapper,
                        @Value("${app.cache.ttl-seconds:10}") long ttlSeconds,
                        ObjectProvider<MeterRegistry> meterRegistry) {

        this.redis = redis;
        this.objectMapper = objectMapper;
        this.ttl = Duration.ofSeconds(ttlSeconds);

        MeterRegistry registry = meterRegistry.getIfAvailable(SimpleMeterRegistry::new);
        this.hits = Counter.builder("commercehub.catalog.cache").tag("result", "hit").register(registry);
        this.misses = Counter.builder("commercehub.catalog.cache").tag("result", "miss").register(registry);
    }

    /** Returns the cached value, or loads it from the database and caches it. */
    public <T> T get(String name, String key, TypeReference<T> type, Supplier<T> loader) {

        if (!redis.enabled()) {
            return loader.get();
        }

        String cacheKey;

        try {

            String version = redis.redis().opsForValue().get(VERSION_KEY);
            cacheKey = "catalog:v" + (version == null ? "0" : version) + ":" + name + ":" + key;

            String json = redis.redis().opsForValue().get(cacheKey);

            if (json != null) {
                hits.increment();
                return objectMapper.readValue(json, type);
            }

        } catch (Exception ex) {

            redis.failed("catalogue cache", ex);
            return loader.get();
        }

        misses.increment();
        T value = loader.get();

        try {
            redis.redis().opsForValue().set(cacheKey, objectMapper.writeValueAsString(value), ttl);
        } catch (Exception ex) {
            redis.failed("catalogue cache", ex);
        }

        return value;
    }

    /**
     * Makes every cached catalogue entry stale. Runs AFTER the current transaction commits,
     * so a reader cannot cache the old data again under the new version before the change is saved.
     */
    public void invalidateAfterCommit() {

        if (!redis.enabled()) {
            return;
        }

        Runnable bumpVersion = () -> {
            try {
                redis.redis().opsForValue().increment(VERSION_KEY);
            } catch (Exception ex) {
                redis.failed("catalogue cache", ex);
            }
        };

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    bumpVersion.run();
                }
            });
        } else {
            bumpVersion.run();
        }
    }
}
