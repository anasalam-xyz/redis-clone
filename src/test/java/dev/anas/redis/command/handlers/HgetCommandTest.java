package dev.anas.redis.command.handlers;

import dev.anas.redis.protocol.Reply;
import dev.anas.redis.store.Store;
import dev.anas.redis.store.Value;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class HgetCommandTest {

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.US_ASCII);
    }

    @Test
    void returnsValueForExistingField() {
        Store store = new Store();
        store.hashSet(bytes("h"), new byte[][]{bytes("f"), bytes("v")});
        Reply r = new HgetCommand(store).execute(new byte[][]{bytes("h"), bytes("f")});
        assertInstanceOf(Reply.BulkString.class, r);
        assertArrayEquals(bytes("v"), ((Reply.BulkString) r).data());
    }

    @Test
    void returnsNullForMissingField() {
        Store store = new Store();
        store.hashSet(bytes("h"), new byte[][]{bytes("a"), bytes("1")});
        Reply r = new HgetCommand(store).execute(new byte[][]{bytes("h"), bytes("missing")});
        assertInstanceOf(Reply.Null.class, r);
    }

    @Test
    void returnsNullForMissingKey() {
        Reply r = new HgetCommand(new Store()).execute(new byte[][]{bytes("h"), bytes("f")});
        assertInstanceOf(Reply.Null.class, r);
    }

    @Test
    void hgetOnWrongTypeReturnsWrongType() {
        Store store = new Store();
        store.set(bytes("h"), new Value.StringValue(bytes("v")), 0L);
        Reply r = new HgetCommand(store).execute(new byte[][]{bytes("h"), bytes("f")});
        assertInstanceOf(Reply.Error.class, r);
        assertTrue(((Reply.Error) r).msg().startsWith("WRONGTYPE"));
    }

    @Test
    void wrongArityReturnsError() {
        HgetCommand cmd = new HgetCommand(new Store());
        assertInstanceOf(Reply.Error.class, cmd.execute(new byte[0][]));
        assertInstanceOf(Reply.Error.class, cmd.execute(new byte[][]{bytes("h")}));
        assertInstanceOf(Reply.Error.class, cmd.execute(new byte[][]{bytes("h"), bytes("f"), bytes("x")}));
    }

    @Test
    void isNotWrite() {
        assertFalse(new HgetCommand(new Store()).isWrite());
    }
}