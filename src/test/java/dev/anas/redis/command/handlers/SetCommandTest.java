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

        Reply r = cmd.execute(new byte[][] { bytes("k"), bytes("v") });

        assertInstanceOf(Reply.SimpleString.class, r);
        assertEquals("OK", ((Reply.SimpleString) r).s());
        assertArrayEquals(bytes("v"), bytesOf(store.get(bytes("k"))));
    }

    @Test
    void overwriteReplacesValue() {
        Store store = new Store();
        SetCommand cmd = new SetCommand(store);

        cmd.execute(new byte[][] { bytes("k"), bytes("old") });
        cmd.execute(new byte[][] { bytes("k"), bytes("new") });

        assertArrayEquals(bytes("new"), bytesOf(store.get(bytes("k"))));
    }

    @Test
    void overwritesAKeyHoldingAList() {
        Store store = new Store();
        store.listPushAll(b("k"), new byte[][] { b("x") }, false);

        Reply reply = new SetCommand(store).execute(new byte[][] { b("k"), b("v") });

        assertEquals(new Reply.SimpleString("OK"), reply);
        Value.StringValue sv = assertInstanceOf(Value.StringValue.class, store.get(b("k")));
        assertArrayEquals(b("v"), sv.bytes());
    }

    @Test
    void setClearsExistingExpiry() {
        Store store = new Store();
        store.set(b("k"), new Value.StringValue(b("old")), System.currentTimeMillis() + 60_000);

        new SetCommand(store).execute(new byte[][] { b("k"), b("new") });

        assertEquals(0L, store.getExpiry(b("k"))); // 0 = no expiry
    }

    @Test
    void wrongArityReturnsError() {
        SetCommand cmd = new SetCommand(new Store());
        assertInstanceOf(Reply.Error.class, cmd.execute(new byte[0][]));
        assertInstanceOf(Reply.Error.class, cmd.execute(new byte[][] { bytes("k") }));
        assertInstanceOf(Reply.Error.class, cmd.execute(new byte[][] { bytes("k"), bytes("v"), bytes("x") }));
    }

    @Test
    void isWrite() {
        assertTrue(new SetCommand(new Store()).isWrite());
    }

    private static byte[] b(String s) {
        return s.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }
}