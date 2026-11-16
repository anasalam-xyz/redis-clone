package dev.anas.redis.command.handlers;

import dev.anas.redis.protocol.Reply;
import dev.anas.redis.store.Store;
import dev.anas.redis.store.Value;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;

import static org.junit.jupiter.api.Assertions.*;

class DecrCommandTest {

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.US_ASCII);
    }

    private static Value.StringValue sv(String s) {
        return new Value.StringValue(bytes(s));
    }

    private static byte[] val(Store store, String key) {
        return ((Value.StringValue) store.get(bytes(key))).bytes();
    }

    @Test
    void decrementsExistingValue() {
        Store store = new Store();
        store.set(bytes("k"), sv("5"), 0L);
        Reply r = new DecrCommand(store).execute(new byte[][]{bytes("k")});
        assertInstanceOf(Reply.Integer.class, r);
        assertEquals(4L, ((Reply.Integer) r).n());
        assertArrayEquals(bytes("4"), val(store, "k"));
    }

    @Test
    void missingKeyTreatedAsMinusOne() {
        Store store = new Store();
        Reply r = new DecrCommand(store).execute(new byte[][]{bytes("k")});
        assertInstanceOf(Reply.Integer.class, r);
        assertEquals(-1L, ((Reply.Integer) r).n());
        assertArrayEquals(bytes("-1"), val(store, "k"));
    }

    @Test
    void nonIntegerValueReturnsError() {
        Store store = new Store();
        store.set(bytes("k"), sv("abc"), 0L);
        Reply r = new DecrCommand(store).execute(new byte[][]{bytes("k")});
        assertInstanceOf(Reply.Error.class, r);
        assertTrue(((Reply.Error) r).msg().startsWith("ERR value is not an integer"));
    }

    @Test
    void wrongTypeReturnsWrongTypeError() {
        Store store = new Store();
        store.set(bytes("k"), new Value.ListValue(new ArrayDeque<>()), 0L);
        Reply r = new DecrCommand(store).execute(new byte[][]{bytes("k")});
        assertInstanceOf(Reply.Error.class, r);
        assertTrue(((Reply.Error) r).msg().startsWith("WRONGTYPE"));
    }

    @Test
    void wrongArityReturnsError() {
        DecrCommand cmd = new DecrCommand(new Store());
        assertInstanceOf(Reply.Error.class, cmd.execute(new byte[0][]));
        assertInstanceOf(Reply.Error.class, cmd.execute(new byte[][]{bytes("a"), bytes("b")}));
    }

    @Test
    void preservesExistingTtl() {
        Store store = new Store();
        store.set(bytes("k"), sv("5"), 0L);
        store.setExpiry(bytes("k"), System.currentTimeMillis() + 10_000);

        new DecrCommand(store).execute(new byte[][]{bytes("k")});

        long expiry = store.getExpiry(bytes("k"));
        assertTrue(expiry > System.currentTimeMillis(), "TTL should be preserved");
    }

    @Test
    void isWrite() {
        assertTrue(new DecrCommand(new Store()).isWrite());
    }
}