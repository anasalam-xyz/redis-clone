package dev.anas.redis.command.handlers;

import dev.anas.redis.protocol.Reply;
import dev.anas.redis.store.Store;
import dev.anas.redis.store.Value;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class PersistCommandTest {

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.US_ASCII);
    }

    @Test
    void removesTtlAndReturnsOne() {
        Store store = new Store();
        store.set(bytes("k"), new Value.StringValue(bytes("v")), 0L);
        store.setExpiry(bytes("k"), System.currentTimeMillis() + 10_000);
        PersistCommand cmd = new PersistCommand(store);

        Reply r = cmd.execute(new byte[][]{bytes("k")});
        assertInstanceOf(Reply.Integer.class, r);
        assertEquals(1L, ((Reply.Integer) r).n());
        assertEquals(0L, store.getExpiry(bytes("k")));
    }

    @Test
    void returnsZeroForKeyWithoutTtl() {
        Store store = new Store();
        store.set(bytes("k"), new Value.StringValue(bytes("v")), 0L);
        PersistCommand cmd = new PersistCommand(store);
        Reply r = cmd.execute(new byte[][]{bytes("k")});
        assertInstanceOf(Reply.Integer.class, r);
        assertEquals(0L, ((Reply.Integer) r).n());
    }

    @Test
    void returnsZeroForMissingKey() {
        PersistCommand cmd = new PersistCommand(new Store());
        Reply r = cmd.execute(new byte[][]{bytes("k")});
        assertInstanceOf(Reply.Integer.class, r);
        assertEquals(0L, ((Reply.Integer) r).n());
    }

    @Test
    void wrongArityReturnsError() {
        PersistCommand cmd = new PersistCommand(new Store());
        assertInstanceOf(Reply.Error.class, cmd.execute(new byte[0][]));
        assertInstanceOf(Reply.Error.class, cmd.execute(new byte[][]{bytes("a"), bytes("b")}));
    }

    @Test
    void isWrite() {
        assertTrue(new PersistCommand(new Store()).isWrite());
    }
}