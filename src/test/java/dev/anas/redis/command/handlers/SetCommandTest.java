package dev.anas.redis.command.handlers;

import dev.anas.redis.protocol.Reply;
import dev.anas.redis.store.Store;
import dev.anas.redis.store.Value;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class SetCommandTest {

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.US_ASCII);
    }

    private static byte[] bytesOf(Value v) {
        return ((Value.StringValue) v).bytes();
    }

    @Test
    void storesValueAndReturnsOk() {
        Store store = new Store();
        SetCommand cmd = new SetCommand(store);

        Reply r = cmd.execute(new byte[][]{bytes("k"), bytes("v")});

        assertInstanceOf(Reply.SimpleString.class, r);
        assertEquals("OK", ((Reply.SimpleString) r).s());
        assertArrayEquals(bytes("v"), bytesOf(store.get(bytes("k"))));
    }

    @Test
    void overwriteReplacesValue() {
        Store store = new Store();
        SetCommand cmd = new SetCommand(store);

        cmd.execute(new byte[][]{bytes("k"), bytes("old")});
        cmd.execute(new byte[][]{bytes("k"), bytes("new")});

        assertArrayEquals(bytes("new"), bytesOf(store.get(bytes("k"))));
    }

    @Test
    void setOnNonStringKeyReturnsWrongType() {
        Store store = new Store();
        // Pre-populate with a list — SET must not overwrite it.
        store.set(bytes("k"), new Value.ListValue(new java.util.ArrayDeque<>()), 0L);
        SetCommand cmd = new SetCommand(store);

        Reply r = cmd.execute(new byte[][]{bytes("k"), bytes("v")});
        assertInstanceOf(Reply.Error.class, r);
        String msg = ((Reply.Error) r).msg();
        assertTrue(msg.startsWith("WRONGTYPE"), "expected WRONGTYPE, got: " + msg);
    }

    @Test
    void wrongArityReturnsError() {
        SetCommand cmd = new SetCommand(new Store());
        assertInstanceOf(Reply.Error.class, cmd.execute(new byte[0][]));
        assertInstanceOf(Reply.Error.class, cmd.execute(new byte[][]{bytes("k")}));
        assertInstanceOf(Reply.Error.class, cmd.execute(new byte[][]{bytes("k"), bytes("v"), bytes("x")}));
    }

    @Test
    void isWrite() {
        assertTrue(new SetCommand(new Store()).isWrite());
    }
}