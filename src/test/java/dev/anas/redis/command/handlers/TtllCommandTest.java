package dev.anas.redis.command.handlers;

import dev.anas.redis.protocol.Reply;
import dev.anas.redis.store.Store;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class TtlCommandTest {

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.US_ASCII);
    }

    @Test
    void returnsMinusTwoForMissingKey() {
        TtlCommand cmd = new TtlCommand(new Store());
        Reply r = cmd.execute(new byte[][]{bytes("k")});
        assertInstanceOf(Reply.Integer.class, r);
        assertEquals(-2L, ((Reply.Integer) r).n());
    }

    @Test
    void returnsMinusOneForKeyWithoutTtl() {
        Store store = new Store();
        store.set(bytes("k"), bytes("v"), 0L);
        TtlCommand cmd = new TtlCommand(store);
        Reply r = cmd.execute(new byte[][]{bytes("k")});
        assertInstanceOf(Reply.Integer.class, r);
        assertEquals(-1L, ((Reply.Integer) r).n());
    }

    @Test
    void returnsRemainingSecondsRoundedDown() {
        Store store = new Store();
        store.set(bytes("k"), bytes("v"), 0L);
        store.setExpiry(bytes("k"), System.currentTimeMillis() + 2500);
        TtlCommand cmd = new TtlCommand(store);
        Reply r = cmd.execute(new byte[][]{bytes("k")});
        assertInstanceOf(Reply.Integer.class, r);
        long n = ((Reply.Integer) r).n();
        // 2.5 s remaining -> TTL returns 2 (truncates), could be 2 or 1 if a second elapsed
        assertTrue(n == 2L || n == 1L, "unexpected TTL: " + n);
    }

    @Test
    void wrongArityReturnsError() {
        TtlCommand cmd = new TtlCommand(new Store());
        assertInstanceOf(Reply.Error.class, cmd.execute(new byte[0][]));
        assertInstanceOf(Reply.Error.class, cmd.execute(new byte[][]{bytes("a"), bytes("b")}));
    }

    @Test
    void isNotWrite() {
        assertFalse(new TtlCommand(new Store()).isWrite());
    }
}