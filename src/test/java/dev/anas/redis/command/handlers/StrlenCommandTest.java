package dev.anas.redis.command.handlers;

import dev.anas.redis.protocol.Reply;
import dev.anas.redis.store.Store;
import dev.anas.redis.store.Value;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;

import static org.junit.jupiter.api.Assertions.*;

class StrlenCommandTest {

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.US_ASCII);
    }

    @Test
    void returnsLengthForExistingString() {
        Store store = new Store();
        store.set(bytes("k"), new Value.StringValue(bytes("hello")), 0L);
        Reply r = new StrlenCommand(store).execute(new byte[][]{bytes("k")});
        assertInstanceOf(Reply.Integer.class, r);
        assertEquals(5L, ((Reply.Integer) r).n());
    }

    @Test
    void returnsZeroForMissingKey() {
        Reply r = new StrlenCommand(new Store()).execute(new byte[][]{bytes("k")});
        assertInstanceOf(Reply.Integer.class, r);
        assertEquals(0L, ((Reply.Integer) r).n());
    }

    @Test
    void returnsByteLengthNotCharLength() {
        // "héllo": 5 chars, but 'é' is 2 bytes in UTF-8, so 6 bytes total.
        byte[] data = "héllo".getBytes(StandardCharsets.UTF_8);
        Store store = new Store();
        store.set(bytes("k"), new Value.StringValue(data), 0L);
        Reply r = new StrlenCommand(store).execute(new byte[][]{bytes("k")});
        assertInstanceOf(Reply.Integer.class, r);
        assertEquals(6L, ((Reply.Integer) r).n());
    }

    @Test
    void wrongTypeReturnsWrongTypeError() {
        Store store = new Store();
        store.set(bytes("k"), new Value.ListValue(new ArrayDeque<>()), 0L);
        Reply r = new StrlenCommand(store).execute(new byte[][]{bytes("k")});
        assertInstanceOf(Reply.Error.class, r);
        assertTrue(((Reply.Error) r).msg().startsWith("WRONGTYPE"));
    }

    @Test
    void wrongArityReturnsError() {
        StrlenCommand cmd = new StrlenCommand(new Store());
        assertInstanceOf(Reply.Error.class, cmd.execute(new byte[0][]));
        assertInstanceOf(Reply.Error.class, cmd.execute(new byte[][]{bytes("a"), bytes("b")}));
    }

    @Test
    void isNotWrite() {
        assertFalse(new StrlenCommand(new Store()).isWrite());
    }
}