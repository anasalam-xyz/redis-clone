package dev.anas.redis.command.handlers;

import dev.anas.redis.protocol.Reply;
import dev.anas.redis.store.Store;
import dev.anas.redis.store.Value;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class PttlCommandTest {

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.US_ASCII);
    }

    @Test
    void returnsMinusTwoForMissingKey() {
        PttlCommand cmd = new PttlCommand(new Store());
        Reply r = cmd.execute(new byte[][]{bytes("k")});
        assertInstanceOf(Reply.Integer.class, r);
        assertEquals(-2L, ((Reply.Integer) r).n());
    }

    @Test
    void returnsMinusOneForKeyWithoutTtl() {
        Store store = new Store();
        store.set(bytes("k"), new Value.StringValue(bytes("v")), 0L);
        PttlCommand cmd = new PttlCommand(store);
        Reply r = cmd.execute(new byte[][]{bytes("k")});
        assertInstanceOf(Reply.Integer.class, r);
        assertEquals(-1L, ((Reply.Integer) r).n());
    }

    @Test
    void returnsRemainingMillis() {
        Store store = new Store();
        store.set(bytes("k"), new Value.StringValue(bytes("v")), 0L);
        store.setExpiry(bytes("k"), System.currentTimeMillis() + 5000);
        PttlCommand cmd = new PttlCommand(store);
        Reply r = cmd.execute(new byte[][]{bytes("k")});
        assertInstanceOf(Reply.Integer.class, r);
        long n = ((Reply.Integer) r).n();
        assertTrue(n > 4000L && n <= 5000L, "unexpected PTTL: " + n);
    }

    @Test
    void wrongArityReturnsError() {
        PttlCommand cmd = new PttlCommand(new Store());
        assertInstanceOf(Reply.Error.class, cmd.execute(new byte[0][]));
        assertInstanceOf(Reply.Error.class, cmd.execute(new byte[][]{bytes("a"), bytes("b")}));
    }

    @Test
    void isNotWrite() {
        assertFalse(new PttlCommand(new Store()).isWrite());
    }
}