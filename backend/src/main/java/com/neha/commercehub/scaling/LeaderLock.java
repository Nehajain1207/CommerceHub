package com.neha.commercehub.scaling;

import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Lets only ONE running copy of the back end do a background job at a time
 * (used for the outbox publisher, so two copies do not send the same events together).
 *
 * The copy that holds the lock keeps renewing it. If that copy crashes, the lock simply
 * expires after a few seconds and another copy takes over.
 *
 * Even without the lock nothing would break: events are sent at least once and the
 * notification service ignores an event it has already handled. The lock just avoids
 * the wasted duplicate work.
 */
@Component
public class LeaderLock {

    /** Renews the lock only if this instance still owns it. */
    private static final DefaultRedisScript<Long> RENEW = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[1]) == ARGV[1] then return redis.call('PEXPIRE', KEYS[1], ARGV[2]) end
            return 0
            """, Long.class);

    private final RedisSupport redis;
    private final String instanceId = UUID.randomUUID().toString();

    public LeaderLock(RedisSupport redis) {
        this.redis = redis;
    }

    /** True if this instance holds (or has just taken) the named lock. */
    public boolean holdOrAcquire(String name, Duration ttl) {

        if (!redis.enabled()) {
            return true;
        }

        try {

            String key = "leader:" + name;

            if (Boolean.TRUE.equals(redis.redis().opsForValue().setIfAbsent(key, instanceId, ttl))) {
                return true;
            }

            Long renewed = redis.redis().execute(RENEW, List.of(key), instanceId, Long.toString(ttl.toMillis()));
            return renewed != null && renewed == 1;

        } catch (Exception ex) {

            // Safe to carry on: duplicates are harmless because receivers are idempotent
            redis.failed("leader lock", ex);
            return true;
        }
    }
}
