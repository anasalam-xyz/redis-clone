package dev.anas.redis.command.handlers;

import static org.junit.jupiter.api.Assertions.*;

import dev.anas.redis.protocol.Reply;
import dev.anas.redis.ratelimit.RateLimiter;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class RatelimitCommandTest {

    private static byte[] b(String s) { return s.getBytes(StandardCharsets.UTF_8); }

    // Dispatcher strips the command name, so args = {key, capacity, refill}.
    private static byte[][] args(byte[] key, String capacity, String refill) {
        return new byte[][]{ key, b(capacity), b(refill) };
    }

    private static int value(Reply r) {
        assertInstanceOf(Reply.Integer.class, r, "expected Reply.Integer, got " + r.getClass());
        return (int) ((Reply.Integer) r).n();
    }

    @Test
    void firstCallAllowed_returnsOne() {
        RatelimitCommand cmd = new RatelimitCommand(new RateLimiter());
        Reply r = cmd.execute(args(b("k"), "5", "1"));
        assertEquals(1, value(r));
    }

    @Test
    void exhaustedBucket_returnsZero() {
        RatelimitCommand cmd = new RatelimitCommand(new RateLimiter());
        assertEquals(1, value(cmd.execute(args(b("k"), "2", "1"))));
        assertEquals(1, value(cmd.execute(args(b("k"), "2", "1"))));
        assertEquals(0, value(cmd.execute(args(b("k"), "2", "1"))));
    }

    @Test
    void differentKeysIndependent_throughCommand() {
        RatelimitCommand cmd = new RatelimitCommand(new RateLimiter());
        assertEquals(1, value(cmd.execute(args(b("a"), "1", "1"))));
        assertEquals(0, value(cmd.execute(args(b("a"), "1", "1"))));
        assertEquals(1, value(cmd.execute(args(b("b"), "1", "1"))));
    }

    @Test
    void wrongArgCount_tooFew_rejected() {
        RatelimitCommand cmd = new RatelimitCommand(new RateLimiter());
        Reply r = cmd.execute(new byte[][]{ b("k"), b("5") });
        assertInstanceOf(Reply.Error.class, r);
        assertTrue(((Reply.Error) r).msg().startsWith("ERR wrong number of arguments"));
    }

    @Test
    void wrongArgCount_tooMany_rejected() {
        RatelimitCommand cmd = new RatelimitCommand(new RateLimiter());
        Reply r = cmd.execute(new byte[][]{ b("k"), b("5"), b("1"), b("extra") });
        assertInstanceOf(Reply.Error.class, r);
        assertTrue(((Reply.Error) r).msg().startsWith("ERR wrong number of arguments"));
    }

    @Test
    void capacityNotAnInteger_rejected() {
        RatelimitCommand cmd = new RatelimitCommand(new RateLimiter());
        Reply r = cmd.execute(args(b("k"), "abc", "1"));
        assertInstanceOf(Reply.Error.class, r);
    }

    @Test
    void capacityZero_rejected() {
        RatelimitCommand cmd = new RatelimitCommand(new RateLimiter());
        Reply r = cmd.execute(args(b("k"), "0", "1"));
        assertInstanceOf(Reply.Error.class, r);
    }

    @Test
    void capacityNegative_rejected() {
        RatelimitCommand cmd = new RatelimitCommand(new RateLimiter());
        Reply r = cmd.execute(args(b("k"), "-5", "1"));
        assertInstanceOf(Reply.Error.class, r);
    }

    @Test
    void refillNotANumber_rejected() {
        RatelimitCommand cmd = new RatelimitCommand(new RateLimiter());
        Reply r = cmd.execute(args(b("k"), "5", "abc"));
        assertInstanceOf(Reply.Error.class, r);
    }

    @Test
    void refillZero_rejected() {
        RatelimitCommand cmd = new RatelimitCommand(new RateLimiter());
        Reply r = cmd.execute(args(b("k"), "5", "0"));
        assertInstanceOf(Reply.Error.class, r);
    }

    @Test
    void refillNegative_rejected() {
        RatelimitCommand cmd = new RatelimitCommand(new RateLimiter());
        Reply r = cmd.execute(args(b("k"), "5", "-1.0"));
        assertInstanceOf(Reply.Error.class, r);
    }

    @Test
    void refillNaN_rejected() {
        RatelimitCommand cmd = new RatelimitCommand(new RateLimiter());
        Reply r = cmd.execute(args(b("k"), "5", "NaN"));
        assertInstanceOf(Reply.Error.class, r,
            "NaN slips past 'refill <= 0' because NaN <= 0 is false; must be rejected by isFinite check");
    }

    @Test
    void refillInfinity_rejected() {
        RatelimitCommand cmd = new RatelimitCommand(new RateLimiter());
        Reply r = cmd.execute(args(b("k"), "5", "Infinity"));
        assertInstanceOf(Reply.Error.class, r);
    }

    @Test
    void refillDecimalAccepted() {
        RatelimitCommand cmd = new RatelimitCommand(new RateLimiter());
        Reply r = cmd.execute(args(b("k"), "5", "0.5"));
        assertEquals(1, value(r), "0.5 is a valid refill rate; first call is allowed");
    }

    @Test
    void keyIsBinarySafe_throughCommand() {
        // Key with NUL and CRLF bytes — must not confuse parsing.
        byte[] weirdKey = new byte[]{ 0x00, 0x0d, 0x0a, 'x' };
        RatelimitCommand cmd = new RatelimitCommand(new RateLimiter());
        Reply r = cmd.execute(args(weirdKey, "5", "1"));
        assertEquals(1, value(r));
    }
}