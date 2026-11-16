package dev.anas.redis.command.handlers;

import dev.anas.redis.protocol.Reply;
import dev.anas.redis.store.Store;
import dev.anas.redis.store.Value;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class LpopCommandTest {

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.US_ASCII);
    }

    @Test
    void popsLeftmostElement() {
        Store store = new Store();
        new RpushCommand(store).execute(new byte[][]{bytes("k"), bytes("a"), bytes("b"), bytes("c")});
        Reply r = new LpopCommand(store).execute(new byte[][]{bytes("k")});
        assertInstanceOf(Reply.BulkString.class, r);
        assertArrayEquals(bytes("a"), ((Reply.BulkString) r).data());
    }

    @Test
    void popFromMissingKeyReturnsNull() {
        Reply r = new LpopCommand(new Store()).execute(new byte[][]{bytes("k")});
        assertInstanceOf(Reply.Null.class, r);
    }

    @Test
    void popLastElementDeletesKey() {
        Store store = new Store();
        new RpushCommand(store).execute(new byte[][]{bytes("k"), bytes("only")});
        new LpopCommand(store).execute(new byte[][]{bytes("k")});
        assertFalse(store.exists(bytes("k")));
    }

    @Test
    void popFromWrongTypeReturnsWrongType() {
        Store store = new Store();
        store.set(bytes("k"), new Value.StringValue(bytes("v")), 0L);
        Reply r = new LpopCommand(store).execute(new byte[][]{bytes("k")});
        assertInstanceOf(Reply.Error.class, r);
        assertTrue(((Reply.Error) r).msg().startsWith("WRONGTYPE"));
    }

    @Test
    void wrongArityReturnsError() {
        LpopCommand cmd = new LpopCommand(new Store());
        assertInstanceOf(Reply.Error.class, cmd.execute(new byte[0][]));
        assertInstanceOf(Reply.Error.class, cmd.execute(new byte[][]{bytes("k"), bytes("x")}));
    }

    @Test
    void isWrite() {
        assertTrue(new LpopCommand(new Store()).isWrite());
    }
}