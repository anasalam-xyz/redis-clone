package dev.anas.redis.command.handlers;

import dev.anas.redis.protocol.Reply;
import dev.anas.redis.store.Store;
import dev.anas.redis.store.Value;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;

import static org.junit.jupiter.api.Assertions.*;

class AppendCommandTest {

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
    void appendsToExistingValue() {
        Store store = new Store();
        store.set(bytes("k"), sv("hello"), 0L);
        Reply r = new AppendCommand(store).execute(new byte[][]{bytes("k"), bytes(" world")});
        assertInstanceOf(Reply.Integer.class, r);
        assertEquals(11L, ((Reply.Integer) r).n());
        assertArrayEquals(bytes("hello world"), val(store, "k"));
    }

    @Test
    void appendToMissingKeyActsLikeSet() {
        Store store = new Store();
        Reply r = new AppendCommand(store).execute(new byte[][]{bytes("k"), bytes("hello")});
        assertInstanceOf(Reply.Integer.class, r);
        assertEquals(5L, ((Reply.Integer) r).n());
        assertArrayEquals(bytes("hello"), val(store, "k"));
    }

    @Test
    void appendsEmptyStringReturnsCurrentLength() {
        Store store = new Store();
        store.set(bytes("k"), sv("abc"), 0L);
        Reply r = new AppendCommand(store).execute(new byte[][]{bytes("k"), new byte[0]});
        assertInstanceOf(Reply.Integer.class, r);
        assertEquals(3L, ((Reply.Integer) r).n());
        assertArrayEquals(bytes("abc"), val(store, "k"));
    }

    @Test
    void appendsBinaryValueUnchanged() {
        Store store = new Store();
        store.set(bytes("k"), sv("A"), 0L);
        byte[] binary = {0x00, (byte) 0xFF, '\r', '\n'};
        Reply r = new AppendCommand(store).execute(new byte[][]{bytes("k"), binary});
        assertInstanceOf(Reply.Integer.class, r);
        assertEquals(5L, ((Reply.Integer) r).n());
        byte[] result = val(store, "k");
        assertEquals(5, result.length);
        assertEquals('A', result[0]);
        assertEquals(0x00, result[1]);
        assertEquals((byte) 0xFF, result[2]);
        assertEquals('\r', result[3]);
        assertEquals('\n', result[4]);
    }

    @Test
    void wrongTypeReturnsWrongTypeError() {
        Store store = new Store();
        store.set(bytes("k"), new Value.ListValue(new ArrayDeque<>()), 0L);
        Reply r = new AppendCommand(store).execute(new byte[][]{bytes("k"), bytes("x")});
        assertInstanceOf(Reply.Error.class, r);
        assertTrue(((Reply.Error) r).msg().startsWith("WRONGTYPE"));
    }

    @Test
    void wrongArityReturnsError() {
        AppendCommand cmd = new AppendCommand(new Store());
        assertInstanceOf(Reply.Error.class, cmd.execute(new byte[0][]));
        assertInstanceOf(Reply.Error.class, cmd.execute(new byte[][]{bytes("k")}));
        assertInstanceOf(Reply.Error.class, cmd.execute(new byte[][]{bytes("k"), bytes("v"), bytes("x")}));
    }

    @Test
    void preservesExistingTtl() {
        Store store = new Store();
        store.set(bytes("k"), sv("hi"), 0L);
        store.setExpiry(bytes("k"), System.currentTimeMillis() + 10_000);

        new AppendCommand(store).execute(new byte[][]{bytes("k"), bytes("!")});

        long expiry = store.getExpiry(bytes("k"));
        assertTrue(expiry > System.currentTimeMillis(), "TTL should be preserved");
    }

    @Test
    void isWrite() {
        assertTrue(new AppendCommand(new Store()).isWrite());
    }
}