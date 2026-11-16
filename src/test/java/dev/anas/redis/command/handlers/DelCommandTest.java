package dev.anas.redis.command.handlers;

import dev.anas.redis.protocol.Reply;
import dev.anas.redis.store.Store;
import dev.anas.redis.store.Value;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class DelCommandTest {

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.US_ASCII);
    }

    private static Value.StringValue sv(String s) {
        return new Value.StringValue(bytes(s));
    }

    @Test
    void returnsOneWhenKeyDeleted() {
        Store store = new Store();
        store.set(bytes("k"), sv("v"), 0L);
        DelCommand cmd = new DelCommand(store);

        Reply r = cmd.execute(new byte[][]{bytes("k")});

        assertInstanceOf(Reply.Integer.class, r);
        assertEquals(1L, ((Reply.Integer) r).n());
        assertNull(store.get(bytes("k")));       // actually gone
    }

    @Test
    void returnsZeroWhenKeyMissing() {
        DelCommand cmd = new DelCommand(new Store());
        Reply r = cmd.execute(new byte[][]{bytes("k")});

        assertInstanceOf(Reply.Integer.class, r);
        assertEquals(0L, ((Reply.Integer) r).n());
    }

    @Test
    void wrongArityReturnsError() {
        DelCommand cmd = new DelCommand(new Store());
        assertInstanceOf(Reply.Error.class, cmd.execute(new byte[0][]));
        assertInstanceOf(Reply.Error.class, cmd.execute(new byte[][]{bytes("a"), bytes("b")}));
    }

    @Test
    void isWrite() {
        assertTrue(new DelCommand(new Store()).isWrite());
    }
}