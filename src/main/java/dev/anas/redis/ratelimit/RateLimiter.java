package dev.anas.redis.ratelimit;

import dev.anas.redis.store.Key;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class RateLimiter {
    private Map<Key, TokenBucket> buckets;

    public RateLimiter() {
        buckets = new ConcurrentHashMap<>();
    }

    public boolean tryAcquire(byte[] key, int capacity, double refill, long nowMs) {
        Key k = new Key(key);

        TokenBucket bucket = buckets.computeIfAbsent(k, x -> new TokenBucket(capacity, refill, nowMs));

        return bucket.tryAcquire(nowMs);
    }
}
