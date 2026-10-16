package com.neha.commercehub.scaling;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * A fast "is there still stock?" check in Redis, in front of the database.
 *
 * In a flash sale thousands of people try to buy the same few units at the same moment.
 * Without this gate every one of them opens a database transaction and queues for the lock
 * on the same product row, so everyone waits, even the people who will be told "sold out".
 *
 * With the gate, Redis keeps a counter of units still available per product. A Lua script
 * checks and decrements it in one atomic step. Customers who cannot get a unit are answered
 * straight from Redis in about a millisecond, and only the few who got a unit go on to the
 * database, which still does the final, exact stock update (the gate never replaces it).
 *
 * Keeping the counter honest:
 *  - it is loaded from the database the first time it is needed and expires after a short
 *    time, after which it is loaded again (so any drift fixes itself);
 *  - units are given back if the order then fails;
 *  - when stock goes back up (cancelled or expired orders, admin changes) the counter is
 *    deleted and reloaded from the database on the next request.
 * If the counter is ever too high, the database simply refuses the extra orders.
 * If Redis is down, the gate steps aside and the database handles everything as before.
 */
@Component
public class StockGate {

    /** Returns the units left after taking, -1 if not enough, -2 if the counter is not loaded. */
    private static final DefaultRedisScript<Long> TAKE = new DefaultRedisScript<>("""
            local current = redis.call('GET', KEYS[1])
            if not current then return -2 end
            local wanted = tonumber(ARGV[1])
            if tonumber(current) < wanted then return -1 end
            return redis.call('DECRBY', KEYS[1], wanted)
            """, Long.class);

    /** Gives units back, but only to a counter that still exists (never creates a wrong one). */
    private static final DefaultRedisScript<Long> GIVE_BACK = new DefaultRedisScript<>("""
            if redis.call('EXISTS', KEYS[1]) == 1 then return redis.call('INCRBY', KEYS[1], ARGV[1]) end
            return -2
            """, Long.class);

    public enum Result { TAKEN, NOT_ENOUGH, SKIPPED }

    private final RedisSupport redis;
    private final Duration ttl;
    private final Counter rejected;

    /** One lock per product, so only one request per server reloads a counter from the database. */
    private final ConcurrentHashMap<Long, Object> loadLocks = new ConcurrentHashMap<>();

    /** When this server last loaded each product's counter (milliseconds). */
    private final ConcurrentHashMap<Long, Long> loadedAt = new ConcurrentHashMap<>();

    public StockGate(RedisSupport redis,
                     @Value("${app.stock-gate.ttl-seconds:30}") long ttlSeconds,
                     ObjectProvider<MeterRegistry> meterRegistry) {

        this.redis = redis;
        this.ttl = Duration.ofSeconds(ttlSeconds);
        this.rejected = Counter.builder("commercehub.stockgate.rejected")
                .description("Checkouts answered 'sold out' by Redis without touching the database")
                .register(meterRegistry.getIfAvailable(SimpleMeterRegistry::new));
    }

    /**
     * Tries to take units for one product.
     * databaseStock is only called when the counter has to be (re)loaded.
     */
    public Result tryTake(long productId, int quantity, LongSupplier databaseStock) {

        if (!redis.enabled()) {
            return Result.SKIPPED;
        }

        try {

            String key = key(productId);
            Long left = take(key, quantity);

            if (left != null && left == -2) {

                // The counter is not loaded yet. When a sale starts, hundreds of requests arrive
                // here together. Only the first one loads the counter from the database; the
                // others see that it was just loaded and leave the lock at once. The lock holds no
                // Redis call for them, so they do not queue behind each other.
                synchronized (loadLocks.computeIfAbsent(productId, id -> new Object())) {

                    long now = System.currentTimeMillis();
                    Long last = loadedAt.get(productId);

                    if (last == null || now - last > 1000) {
                        redis.redis().opsForValue().setIfAbsent(key, Long.toString(databaseStock.getAsLong()), ttl);
                        loadedAt.put(productId, now);
                    }
                }

                // Every request takes its units in parallel, outside the lock
                left = take(key, quantity);
            }

            if (left == null || left == -2) {
                return Result.SKIPPED;
            }

            if (left == -1) {
                rejected.increment();
                return Result.NOT_ENOUGH;
            }

            return Result.TAKEN;

        } catch (Exception ex) {

            redis.failed("stock gate", ex);
            return Result.SKIPPED;
        }
    }

    /** Puts units back when the order could not be placed after all. */
    public void giveBack(long productId, int quantity) {

        if (!redis.enabled()) {
            return;
        }

        try {
            redis.redis().execute(GIVE_BACK, List.of(key(productId)), Integer.toString(quantity));
        } catch (Exception ex) {
            redis.failed("stock gate", ex);
        }
    }

    /** Drops the counter after the current transaction commits; the next request reloads it from the database. */
    public void forgetAfterCommit(long productId) {

        if (!redis.enabled()) {
            return;
        }

        Runnable forget = () -> {
            try {
                redis.redis().delete(key(productId));
            } catch (Exception ex) {
                redis.failed("stock gate", ex);
            }
        };

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    forget.run();
                }
            });
        } else {
            forget.run();
        }
    }

    private Long take(String key, int quantity) {
        return redis.redis().execute(TAKE, List.of(key), Integer.toString(quantity));
    }

    private static String key(long productId) {
        return "stock:" + productId;
    }
}
