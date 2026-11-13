package dev.anas.redis.command.handlers;

import dev.anas.redis.protocol.Reply;
import dev.anas.redis.store.Store;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class ExistsCommandTest {

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.US_ASCII);
    }

    @Test
    void returnsOneWhenKeyExists() {
        Store store = new Store();
        store.set(bytes("k"), bytes("v"), 0L);
        ExistsCommand cmd = new ExistsCommand(store);

        Reply r = cmd.execute(new byte[][]{bytes("k")});

        assertInstanceOf(Reply.Integer.class, r);
        assertEquals(1L, ((Reply.Integer) r).n());
    }

    @Test
    void returnsZeroWhenKeyMissing() {
        ExistsCommand cmd = new ExistsCommand(new Store());
        Reply r = cmd.execute(new byte[][]{bytes("k")});

        assertInstanceOf(Reply.Integer.class, r);
        assertEquals(0L, ((Reply.Integer) r).n());
    }

    @Test
    void wrongArityReturnsError() {
        ExistsCommand cmd = new ExistsCommand(new Store());
        assertInstanceOf(Reply.Error.class, cmd.execute(new byte[0][]));
        assertInstanceOf(Reply.Error.class, cmd.execute(new byte[][]{bytes("a"), bytes("b")}));
    }

    @Test
    void isNotWrite() {
        assertFalse(new ExistsCommand(new Store()).isWrite());
    }
}