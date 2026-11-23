package dev.anas.redis.ratelimit;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class RateLimiterTest {

    private static byte[] b(String s) { return s.getBytes(StandardCharsets.UTF_8); }

    @Test
    void differentKeysAreIndependent() {
        RateLimiter rl = new RateLimiter();
        assertTrue(rl.tryAcquire(b("a"), 1, 1.0, 0L));
        assertFalse(rl.tryAcquire(b("a"), 1, 1.0, 0L));
        assertTrue(rl.tryAcquire(b("b"), 1, 1.0, 0L), "key b must start full");
    }

    @Test
    void sameKeyRetainsState() {
        RateLimiter rl = new RateLimiter();
        assertTrue(rl.tryAcquire(b("k"), 2, 1.0, 0L));
        assertTrue(rl.tryAcquire(b("k"), 2, 1.0, 0L));
        assertFalse(rl.tryAcquire(b("k"), 2, 1.0, 0L));
        assertTrue(rl.tryAcquire(b("k"), 2, 1.0, 1000L), "1 token refilled after 1s");
    }

    @Test
    void firstCallDefinesBucket_configIsIgnoredAfterward() {
        // First call sets capacity=2, refill=1.0.
        // Later calls with different config must not rebuild the bucket.
        RateLimiter rl = new RateLimiter();
        assertTrue(rl.tryAcquire(b("k"), 2, 1.0, 0L));
        assertTrue(rl.tryAcquire(b("k"), 2, 1.0, 0L));
        assertFalse(rl.tryAcquire(b("k"), 2, 1.0, 0L));
        // Even with capacity=100 refill=100.0, the existing bucket wins:
        assertFalse(rl.tryAcquire(b("k"), 100, 100.0, 0L));
    }

    @Test
    void keyIsBinarySafe() {
        // Two byte arrays that would collide as Strings if you did new String(bytes)
        // but must remain distinct as Keys.
        byte[] k1 = new byte[]{ (byte) 0xC3, (byte) 0x28 }; // invalid UTF-8
        byte[] k2 = new byte[]{ (byte) 0xC3, (byte) 0x29 };

        RateLimiter rl = new RateLimiter();
        assertTrue(rl.tryAcquire(k1, 1, 1.0, 0L));
        assertFalse(rl.tryAcquire(k1, 1, 1.0, 0L));
        assertTrue(rl.tryAcquire(k2, 1, 1.0, 0L), "k2 is a different key");
    }
}