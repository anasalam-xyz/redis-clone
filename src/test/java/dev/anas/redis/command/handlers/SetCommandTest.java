package dev.anas.redis.command.handlers;

import dev.anas.redis.protocol.Reply;
import dev.anas.redis.store.Store;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class SetCommandTest {

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.US_ASCII);
    }

    @Test
    void storesValueAndReturnsOk() {
        Store store = new Store();
        SetCommand cmd = new SetCommand(store);

        Reply r = cmd.execute(new byte[][]{bytes("k"), bytes("v")});

        assertInstanceOf(Reply.SimpleString.class, r);
        assertEquals("OK", ((Reply.SimpleString) r).s());
        // Verify the value actually landed in the store.
        assertArrayEquals(bytes("v"), store.get(bytes("k")));
    }

    @Test
    void overwriteReplacesValue() {
        Store store = new Store();
        SetCommand cmd = new SetCommand(store);

        cmd.execute(new byte[][]{bytes("k"), bytes("old")});
        cmd.execute(new byte[][]{bytes("k"), bytes("new")});

        assertArrayEquals(bytes("new"), store.get(bytes("k")));
    }

    @Test
    void wrongArityReturnsError() {
        Store store = new Store();
        SetCommand cmd = new SetCommand(store);

        assertInstanceOf(Reply.Error.class, cmd.execute(new byte[0][]));                          // 0 args
        assertInstanceOf(Reply.Error.class, cmd.execute(new byte[][]{bytes("k")}));               // 1 arg
        assertInstanceOf(Reply.Error.class, cmd.execute(new byte[][]{bytes("k"), bytes("v"), bytes("x")})); // 3 args
    }

    @Test
    void isWrite() {
        assertTrue(new SetCommand(new Store()).isWrite());
    }
}