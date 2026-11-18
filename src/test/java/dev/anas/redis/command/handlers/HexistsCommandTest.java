package dev.anas.redis.command.handlers;

import dev.anas.redis.protocol.Reply;
import dev.anas.redis.store.Store;
import dev.anas.redis.store.Value;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class HexistsCommandTest {

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.US_ASCII);
    }

    @Test
    void returnsOneForExistingField() {
        Store store = new Store();
        store.hashSet(bytes("h"), new byte[][]{bytes("f"), bytes("v")});
        Reply r = new HexistsCommand(store).execute(new byte[][]{bytes("h"), bytes("f")});
        assertInstanceOf(Reply.Integer.class, r);
        assertEquals(1L, ((Reply.Integer) r).n());
    }

    @Test
    void returnsZeroForMissingField() {
        Store store = new Store();
        store.hashSet(bytes("h"), new byte[][]{bytes("a"), bytes("1")});
        Reply r = new HexistsCommand(store).execute(new byte[][]{bytes("h"), bytes("missing")});
        assertEquals(0L, ((Reply.Integer) r).n());
    }

    @Test
    void returnsZeroForMissingKey() {
        Reply r = new HexistsCommand(new Store()).execute(new byte[][]{bytes("h"), bytes("f")});
        assertEquals(0L, ((Reply.Integer) r).n());
    }

    @Test
    void hexistsOnWrongTypeReturnsWrongType() {
        Store store = new Store();
        store.set(bytes("h"), new Value.StringValue(bytes("v")), 0L);
        Reply r = new HexistsCommand(store).execute(new byte[][]{bytes("h"), bytes("f")});
        assertInstanceOf(Reply.Error.class, r);
        assertTrue(((Reply.Error) r).msg().startsWith("WRONGTYPE"));
    }

    @Test
    void wrongArityReturnsError() {
        HexistsCommand cmd = new HexistsCommand(new Store());
        assertInstanceOf(Reply.Error.class, cmd.execute(new byte[0][]));
        assertInstanceOf(Reply.Error.class, cmd.execute(new byte[][]{bytes("h")}));
        assertInstanceOf(Reply.Error.class, cmd.execute(new byte[][]{bytes("h"), bytes("f"), bytes("x")}));
    }

    @Test
    void isNotWrite() {
        assertFalse(new HexistsCommand(new Store()).isWrite());
    }
}