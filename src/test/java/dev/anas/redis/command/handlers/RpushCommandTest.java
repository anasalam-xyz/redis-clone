package dev.anas.redis.command.handlers;

import dev.anas.redis.protocol.Reply;
import dev.anas.redis.store.Store;
import dev.anas.redis.store.Value;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class RpushCommandTest {

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.US_ASCII);
    }

    @Test
    void pushToMissingKeyCreatesList() {
        Store store = new Store();
        Reply r = new RpushCommand(store).execute(new byte[][]{bytes("k"), bytes("a")});
        assertInstanceOf(Reply.Integer.class, r);
        assertEquals(1L, ((Reply.Integer) r).n());
        assertTrue(store.exists(bytes("k")));
    }

    @Test
    void pushMultipleElementsInRightOrder() {
        Store store = new Store();
        // RPUSH k a b c → list becomes [a, b, c]
        Reply r = new RpushCommand(store).execute(
            new byte[][]{bytes("k"), bytes("a"), bytes("b"), bytes("c")});
        assertEquals(3L, ((Reply.Integer) r).n());

        Reply range = new LrangeCommand(store).execute(
            new byte[][]{bytes("k"), bytes("0"), bytes("-1")});
        var items = ((Reply.Array) range).items();
        assertArrayEquals(bytes("a"), ((Reply.BulkString) items.get(0)).data());
        assertArrayEquals(bytes("b"), ((Reply.BulkString) items.get(1)).data());
        assertArrayEquals(bytes("c"), ((Reply.BulkString) items.get(2)).data());
    }

    @Test
    void pushToWrongTypeReturnsWrongType() {
        Store store = new Store();
        store.set(bytes("k"), new Value.StringValue(bytes("v")), 0L);
        Reply r = new RpushCommand(store).execute(new byte[][]{bytes("k"), bytes("a")});
        assertInstanceOf(Reply.Error.class, r);
        assertTrue(((Reply.Error) r).msg().startsWith("WRONGTYPE"));
    }

    @Test
    void wrongArityReturnsError() {
        RpushCommand cmd = new RpushCommand(new Store());
        assertInstanceOf(Reply.Error.class, cmd.execute(new byte[0][]));
        assertInstanceOf(Reply.Error.class, cmd.execute(new byte[][]{bytes("k")}));
    }

    @Test
    void isWrite() {
        assertTrue(new RpushCommand(new Store()).isWrite());
    }
}