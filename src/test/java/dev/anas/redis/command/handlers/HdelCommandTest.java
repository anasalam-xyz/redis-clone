package dev.anas.redis.command.handlers;

import dev.anas.redis.protocol.Reply;
import dev.anas.redis.store.Store;
import dev.anas.redis.store.Value;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class HdelCommandTest {

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.US_ASCII);
    }

    @Test
    void deletesSingleField() {
        Store store = new Store();
        store.hashSet(bytes("h"), new byte[][] { bytes("a"), bytes("1"), bytes("b"), bytes("2") });
        Reply r = new HdelCommand(store).execute(new byte[][] { bytes("h"), bytes("a") });
        assertInstanceOf(Reply.Integer.class, r);
        assertEquals(1L, ((Reply.Integer) r).n());

        // 'b' should still be there
        Reply check = new HgetCommand(store).execute(new byte[][] { bytes("h"), bytes("b") });
        assertInstanceOf(Reply.BulkString.class, check);
        assertArrayEquals(bytes("2"), ((Reply.BulkString) check).data());

        // 'a' should be gone
        Reply gone = new HgetCommand(store).execute(new byte[][] { bytes("h"), bytes("a") });
        assertInstanceOf(Reply.Null.class, gone);
    }

    @Test
    void deletesMultipleFields() {
        Store store = new Store();
        store.hashSet(bytes("h"),
                new byte[][] { bytes("a"), bytes("1"), bytes("b"), bytes("2"), bytes("c"), bytes("3") });
        Reply r = new HdelCommand(store).execute(new byte[][] { bytes("h"), bytes("a"), bytes("c") });
        assertEquals(2L, ((Reply.Integer) r).n());
    }

    @Test
    void deletingMissingFieldReturnsZero() {
        Store store = new Store();
        store.hashSet(bytes("h"), new byte[][] { bytes("a"), bytes("1") });
        Reply r = new HdelCommand(store).execute(new byte[][] { bytes("h"), bytes("missing") });
        assertEquals(0L, ((Reply.Integer) r).n());
        // The hash should still exist after this no-op delete.
        assertTrue(store.exists(bytes("h")));
    }

    @Test
    void deletingLastFieldRemovesKey() {
        Store store = new Store();
        store.hashSet(bytes("h"), new byte[][] { bytes("only"), bytes("v") });
        new HdelCommand(store).execute(new byte[][] { bytes("h"), bytes("only") });
        assertFalse(store.exists(bytes("h")));
    }

    @Test
    void hdelOnWrongTypeReturnsWrongType() {
        Store store = new Store();
        store.set(bytes("h"), new Value.StringValue(bytes("v")), 0L);
        Reply r = new HdelCommand(store).execute(new byte[][] { bytes("h"), bytes("f") });
        assertInstanceOf(Reply.Error.class, r);
        assertTrue(((Reply.Error) r).msg().startsWith("WRONGTYPE"));
    }

    @Test
    void wrongArityReturnsError() {
        HdelCommand cmd = new HdelCommand(new Store());
        assertInstanceOf(Reply.Error.class, cmd.execute(new byte[0][]));
        assertInstanceOf(Reply.Error.class, cmd.execute(new byte[][] { bytes("h") }));
    }

    @Test
    void isWrite() {
        assertTrue(new HdelCommand(new Store()).isWrite());
    }
}