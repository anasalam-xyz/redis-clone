package dev.anas.redis.ratelimit;

public class TokenBucket {
    int capacity;
    double refillPerSec;
    double tokens;
    long lastRefillMs;

    TokenBucket(int capacity, double refillPerSec, long initialNowMs) {
        if(capacity <= 0 || refillPerSec <= 0) {
            throw new IllegalArgumentException();
        }
        this.capacity = capacity;
        this.refillPerSec = refillPerSec;
        lastRefillMs = initialNowMs;
        tokens = capacity;
    }

    synchronized boolean tryAcquire(long nowMs) {
        long elapsedMs = nowMs - lastRefillMs;
        if(elapsedMs < 0) elapsedMs = 0;
        double refill = elapsedMs / 1000.0 * refillPerSec;
        tokens = Math.min(capacity, tokens + refill);
        lastRefillMs = Math.max(lastRefillMs, nowMs);

        if(tokens >= 1) {
            tokens -= 1.0;
            return true;
        }

        return false;
    }
}
