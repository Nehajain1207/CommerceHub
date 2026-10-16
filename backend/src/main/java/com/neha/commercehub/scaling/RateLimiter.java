package com.neha.commercehub.scaling;

import com.neha.commercehub.common.TooManyRequestsException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * Limits how often one user can call an endpoint, shared across all running copies of the
 * back end (the count lives in Redis, not in one server's memory).
 *
 * Fixed window: the first request creates a counter that expires after the window; each
 * request adds one; above the limit the request is refused with HTTP 429.
 * The increment and the expiry are set in one Lua script, so a counter can never be left
 * without an expiry (which would block a user forever).
 */
@Component
public class RateLimiter {

    private static final DefaultRedisScript<Long> HIT = new DefaultRedisScript<>("""
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[1]) end
            return count
            """, Long.class);

    private final RedisSupport redis;
    private final MeterRegistry meterRegistry;

    public RateLimiter(RedisSupport redis, ObjectProvider<MeterRegistry> meterRegistry) {
        this.redis = redis;
        this.meterRegistry = meterRegistry.getIfAvailable(SimpleMeterRegistry::new);
    }

    /** Throws TooManyRequestsException when the caller is over the limit. */
    public void check(String bucket, String caller, int limit, Duration window, String message) {

        if (!allow(bucket, caller, limit, window)) {
            throw new TooManyRequestsException(message);
        }
    }

    public boolean allow(String bucket, String caller, int limit, Duration window) {

        if (!redis.enabled()) {
            return true;
        }

        try {

            String key = "ratelimit:" + bucket + ":" + caller.toLowerCase();
            Long count = redis.redis().execute(HIT, List.of(key), Long.toString(window.toMillis()));

            if (count != null && count > limit) {
                Counter.builder("commercehub.ratelimit.rejected").tag("bucket", bucket)
                        .register(meterRegistry).increment();
                return false;
            }

            return true;

        } catch (Exception ex) {

            // If Redis is down we let the request through rather than block every customer
            redis.failed("rate limiter", ex);
            return true;
        }
    }
}
