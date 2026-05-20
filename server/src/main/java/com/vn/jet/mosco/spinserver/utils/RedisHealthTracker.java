package com.vn.jet.mosco.spinserver.utils;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Cooldown-based Circuit Breaker for Redis connections.
 * Prevents OutOfMemoryErrors caused by Lettuce connection queueing when Redis is offline.
 */
public class RedisHealthTracker {
    private static final AtomicBoolean isRedisAvailable = new AtomicBoolean(true);
    private static long lastCheckTime = 0;
    private static final long COOLDOWN_MS = 10000; // 10 seconds

    public static boolean isAvailable() {
        if (!isRedisAvailable.get()) {
            long now = System.currentTimeMillis();
            if (now - lastCheckTime > COOLDOWN_MS) {
                // Cooldown elapsed, allow checking Redis again
                return true;
            }
            return false;
        }
        return true;
    }

    public static void reportSuccess() {
        isRedisAvailable.set(true);
    }

    public static void reportFailure() {
        if (isRedisAvailable.get()) {
            isRedisAvailable.set(false);
            lastCheckTime = System.currentTimeMillis();
        }
    }
}
