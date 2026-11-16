package dev.anas.redis.command.handlers;

import dev.anas.redis.protocol.Reply;
import dev.anas.redis.store.Store;
import dev.anas.redis.store.Value;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;

import static org.junit.jupiter.api.Assertions.*;

class GetCommandTest {

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.US_ASCII);
    }

    @Test
    void returnsBulkStringForExistingKey() {
        Store store = new Store();
        store.set(bytes("k"), new Value.StringValue(bytes("v")), 0L);
        GetCommand cmd = new GetCommand(store);

        Reply r = cmd.execute(new byte[][]{bytes("k")});

        assertInstanceOf(Reply.BulkString.class, r);
        assertArrayEquals(bytes("v"), ((Reply.BulkString) r).data());
    }

    @Test
    void returnsNullForMissingKey() {
        GetCommand cmd = new GetCommand(new Store());
        Reply r = cmd.execute(new byte[][]{bytes("k")});
        assertInstanceOf(Reply.Null.class, r);
    }

    @Test
    void returnsBinaryValueUnchanged() {
        Store store = new Store();
        byte[] value = {0x00, (byte) 0xFF, '\r', '\n', (byte) 0x80};
        store.set(bytes("k"), new Value.StringValue(value), 0L);
        GetCommand cmd = new GetCommand(store);

        Reply r = cmd.execute(new byte[][]{bytes("k")});

        assertInstanceOf(Reply.BulkString.class, r);
        assertArrayEquals(value, ((Reply.BulkString) r).data());
    }

    @Test
    void getOnListKeyReturnsWrongType() {
        Store store = new Store();
        store.set(bytes("k"), new Value.ListValue(new ArrayDeque<>()), 0L);
        GetCommand cmd = new GetCommand(store);

        Reply r = cmd.execute(new byte[][]{bytes("k")});
        assertInstanceOf(Reply.Error.class, r);
        assertTrue(((Reply.Error) r).msg().startsWith("WRONGTYPE"));
    }

    @Test
    void getOnHashKeyReturnsWrongType() {
        Store store = new Store();
        store.set(bytes("k"), new Value.HashValue(new java.util.HashMap<>()), 0L);
        GetCommand cmd = new GetCommand(store);

        Reply r = cmd.execute(new byte[][]{bytes("k")});
        assertInstanceOf(Reply.Error.class, r);
        assertTrue(((Reply.Error) r).msg().startsWith("WRONGTYPE"));
    }

    @Test
    void wrongArityReturnsError() {
        GetCommand cmd = new GetCommand(new Store());
        assertInstanceOf(Reply.Error.class, cmd.execute(new byte[0][]));
        assertInstanceOf(Reply.Error.class, cmd.execute(new byte[][]{bytes("k"), bytes("v")}));
    }

    @Test
    void isNotWrite() {
        assertFalse(new GetCommand(new Store()).isWrite());
    }
}