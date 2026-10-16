package com.neha.commercehub.scaling;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Shared access to Redis for the cache, the stock gate, the rate limiter and the leader lock.
 *
 * Rule for every Redis feature in this app: Redis makes things FASTER, it is never needed
 * to be CORRECT. PostgreSQL is always the source of truth. So if Redis is down or slow,
 * each feature simply steps aside and the request carries on against the database.
 */
@Component
public class RedisSupport {

    private static final Logger log = LoggerFactory.getLogger(RedisSupport.class);

    private final StringRedisTemplate redis;
    private final boolean enabled;
    private final AtomicLong lastWarningAt = new AtomicLong();

    public RedisSupport(ObjectProvider<StringRedisTemplate> redis,
                        @Value("${app.redis.enabled:false}") boolean enabled) {

        this.redis = redis.getIfAvailable();
        this.enabled = enabled && this.redis != null;
    }

    public boolean enabled() {
        return enabled;
    }

    public StringRedisTemplate redis() {
        return redis;
    }

    /** Logs a Redis problem at most once a minute, so an outage does not flood the logs. */
    public void failed(String feature, Exception ex) {

        long now = System.currentTimeMillis();
        long last = lastWarningAt.get();

        if (now - last > 60_000 && lastWarningAt.compareAndSet(last, now)) {
            log.warn("Redis is not available for the {} ({}). Carrying on without it.",
                    feature, ex.getClass().getSimpleName());
        }
    }
}
