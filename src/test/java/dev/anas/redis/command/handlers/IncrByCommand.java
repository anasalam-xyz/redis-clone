package dev.anas.redis.command.handlers;

import dev.anas.redis.protocol.Reply;
import dev.anas.redis.store.Store;
import dev.anas.redis.store.Value;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;

import static org.junit.jupiter.api.Assertions.*;

class IncrByCommandTest {

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
    void incrementsByAmount() {
        Store store = new Store();
        store.set(bytes("k"), sv("5"), 0L);
        Reply r = new IncrByCommand(store).execute(new byte[][]{bytes("k"), bytes("10")});
        assertInstanceOf(Reply.Integer.class, r);
        assertEquals(15L, ((Reply.Integer) r).n());
        assertArrayEquals(bytes("15"), val(store, "k"));
    }

    @Test
    void missingKeyTreatedAsZeroPlusIncrement() {
        Store store = new Store();
        Reply r = new IncrByCommand(store).execute(new byte[][]{bytes("k"), bytes("7")});
        assertInstanceOf(Reply.Integer.class, r);
        assertEquals(7L, ((Reply.Integer) r).n());
        assertArrayEquals(bytes("7"), val(store, "k"));
    }

    @Test
    void negativeIncrementDecrements() {
        Store store = new Store();
        store.set(bytes("k"), sv("10"), 0L);
        Reply r = new IncrByCommand(store).execute(new byte[][]{bytes("k"), bytes("-3")});
        assertInstanceOf(Reply.Integer.class, r);
        assertEquals(7L, ((Reply.Integer) r).n());
    }

    @Test
    void nonIntegerIncrementReturnsError() {
        IncrByCommand cmd = new IncrByCommand(new Store());
        Reply r = cmd.execute(new byte[][]{bytes("k"), bytes("abc")});
        assertInstanceOf(Reply.Error.class, r);
        assertTrue(((Reply.Error) r).msg().startsWith("ERR value is not an integer"));
    }

    @Test
    void nonIntegerStoredValueReturnsError() {
        Store store = new Store();
        store.set(bytes("k"), sv("abc"), 0L);
        Reply r = new IncrByCommand(store).execute(new byte[][]{bytes("k"), bytes("1")});
        assertInstanceOf(Reply.Error.class, r);
        assertTrue(((Reply.Error) r).msg().startsWith("ERR value is not an integer"));
    }

    @Test
    void wrongTypeReturnsWrongTypeError() {
        Store store = new Store();
        store.set(bytes("k"), new Value.ListValue(new ArrayDeque<>()), 0L);
        Reply r = new IncrByCommand(store).execute(new byte[][]{bytes("k"), bytes("1")});
        assertInstanceOf(Reply.Error.class, r);
        assertTrue(((Reply.Error) r).msg().startsWith("WRONGTYPE"));
    }

    @Test
    void wrongArityReturnsError() {
        IncrByCommand cmd = new IncrByCommand(new Store());
        assertInstanceOf(Reply.Error.class, cmd.execute(new byte[0][]));
        assertInstanceOf(Reply.Error.class, cmd.execute(new byte[][]{bytes("k")}));
        assertInstanceOf(Reply.Error.class, cmd.execute(new byte[][]{bytes("k"), bytes("1"), bytes("x")}));
    }

    @Test
    void preservesExistingTtl() {
        Store store = new Store();
        store.set(bytes("k"), sv("5"), 0L);
        store.setExpiry(bytes("k"), System.currentTimeMillis() + 10_000);

        new IncrByCommand(store).execute(new byte[][]{bytes("k"), bytes("1")});

        long expiry = store.getExpiry(bytes("k"));
        assertTrue(expiry > System.currentTimeMillis(), "TTL should be preserved");
    }

    @Test
    void isWrite() {
        assertTrue(new IncrByCommand(new Store()).isWrite());
    }
}