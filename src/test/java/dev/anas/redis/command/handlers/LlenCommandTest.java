package dev.anas.redis.command.handlers;

import dev.anas.redis.protocol.Reply;
import dev.anas.redis.store.Store;
import dev.anas.redis.store.Value;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class LlenCommandTest {

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.US_ASCII);
    }

    @Test
    void returnsLengthForExistingList() {
        Store store = new Store();
        new RpushCommand(store).execute(new byte[][]{bytes("k"), bytes("a"), bytes("b"), bytes("c")});
        Reply r = new LlenCommand(store).execute(new byte[][]{bytes("k")});
        assertInstanceOf(Reply.Integer.class, r);
        assertEquals(3L, ((Reply.Integer) r).n());
    }

    @Test
    void returnsZeroForMissingKey() {
        Reply r = new LlenCommand(new Store()).execute(new byte[][]{bytes("k")});
        assertInstanceOf(Reply.Integer.class, r);
        assertEquals(0L, ((Reply.Integer) r).n());
    }

    @Test
    void returnsLengthAccumulatingPushes() {
        Store store = new Store();
        new RpushCommand(store).execute(new byte[][]{bytes("k"), bytes("a")});
        new RpushCommand(store).execute(new byte[][]{bytes("k"), bytes("b")});
        Reply r = new LlenCommand(store).execute(new byte[][]{bytes("k")});
        assertEquals(2L, ((Reply.Integer) r).n());
    }

    @Test
    void returnsWrongTypeForNonList() {
        Store store = new Store();
        store.set(bytes("k"), new Value.StringValue(bytes("v")), 0L);
        Reply r = new LlenCommand(store).execute(new byte[][]{bytes("k")});
        assertInstanceOf(Reply.Error.class, r);
        assertTrue(((Reply.Error) r).msg().startsWith("WRONGTYPE"));
    }

    @Test
    void wrongArityReturnsError() {
        LlenCommand cmd = new LlenCommand(new Store());
        assertInstanceOf(Reply.Error.class, cmd.execute(new byte[0][]));
        assertInstanceOf(Reply.Error.class, cmd.execute(new byte[][]{bytes("a"), bytes("b")}));
    }

    @Test
    void isNotWrite() {
        assertFalse(new LlenCommand(new Store()).isWrite());
    }
}