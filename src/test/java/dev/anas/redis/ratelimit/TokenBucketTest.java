package dev.anas.redis.ratelimit;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class TokenBucketTest {

    // Bucket starts full. capacity=3, refill=1 token/sec, starting at t=0ms.
    private TokenBucket bucket(int capacity, double refillPerSec) {
        return new TokenBucket(capacity, refillPerSec, 0L);
    }

    @Test
    void freshBucketIsFull_allowsUpToCapacity() {
        TokenBucket b = bucket(3, 1.0);
        assertTrue(b.tryAcquire(0));
        assertTrue(b.tryAcquire(0));
        assertTrue(b.tryAcquire(0));
    }

    @Test
    void emptyBucketRejects() {
        TokenBucket b = bucket(3, 1.0);
        b.tryAcquire(0);
        b.tryAcquire(0);
        b.tryAcquire(0);
        assertFalse(b.tryAcquire(0), "4th request with 0 elapsed must be rejected");
    }

    @Test
    void oneRefillIntervalRestoresOneToken() {
        TokenBucket b = bucket(3, 1.0);
        for (int i = 0; i < 3; i++)
            b.tryAcquire(0);
        assertFalse(b.tryAcquire(0));
        assertTrue(b.tryAcquire(1000), "after 1s, 1 token refilled");
        assertFalse(b.tryAcquire(1000), "only 1 token was refilled");
    }

    @Test
    void refillAccumulatesButCapsAtCapacity() {
        TokenBucket b = bucket(3, 1.0);
        for (int i = 0; i < 3; i++)
            b.tryAcquire(0);
        // 60 seconds elapsed: would be 60 tokens, but cap is 3.
        assertTrue(b.tryAcquire(60_000));
        assertTrue(b.tryAcquire(60_000));
        assertTrue(b.tryAcquire(60_000));
        assertFalse(b.tryAcquire(60_000), "must not exceed capacity");
    }

    @Test
    void fractionalRefill_works() {
        // 0.5 tokens/sec => 1 token every 2000ms
        TokenBucket b = bucket(3, 0.5);
        for (int i = 0; i < 3; i++)
            b.tryAcquire(0);
        assertFalse(b.tryAcquire(1000), "only 0.5 token refilled; not enough");
        assertTrue(b.tryAcquire(2000), "1.0 token total refilled by now");
    }

    @Test
    void partialFractionSurvivesAcrossCalls() {
        TokenBucket b = bucket(3, 0.5);
        for (int i = 0; i < 3; i++)
            b.tryAcquire(0); // tokens = 0
        b.tryAcquire(1000); // tokens = 0.5, false
        assertTrue(b.tryAcquire(2000), "1.0 accumulated, one consumed"); // tokens = 0.0, true
        assertFalse(b.tryAcquire(2500), "0.25 accumulated, not enough"); // tokens = 0.25, false
        assertFalse(b.tryAcquire(3000), "0.5 accumulated, still not enough"); // tokens = 0.5, false
        assertTrue(b.tryAcquire(4000), "1.5 accumulated, one consumed"); // tokens = 0.0, true
    }

    @Test
    void negativeElapsedTimeDoesNotDrain() {
        // clock going backwards must not add negative tokens
        TokenBucket b = bucket(3, 1.0);
        b.tryAcquire(1000);
        assertTrue(b.tryAcquire(0)); // backwards clock: must not corrupt
    }

    @Test
    void rejectsNonPositiveCapacity() {
        assertThrows(IllegalArgumentException.class, () -> new TokenBucket(0, 1.0, 0L));
        assertThrows(IllegalArgumentException.class, () -> new TokenBucket(-1, 1.0, 0L));
    }

    @Test
    void rejectsNonPositiveRefill() {
        assertThrows(IllegalArgumentException.class, () -> new TokenBucket(3, 0, 0L));
        assertThrows(IllegalArgumentException.class, () -> new TokenBucket(3, -0.5, 0L));
    }
}