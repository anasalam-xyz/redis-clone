package dev.anas.redis.command.handlers;

import dev.anas.redis.protocol.Reply;
import dev.anas.redis.store.Store;
import dev.anas.redis.store.Value;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class PexpireCommandTest {

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.US_ASCII);
    }

    private static Value.StringValue sv(String s) {
        return new Value.StringValue(bytes(s));
    }

    @Test
    void pexpireOnExistingKeyReturnsOneAndSetsTtl() {
        Store store = new Store();
        store.set(bytes("k"), sv("v"), 0L);
        PexpireCommand cmd = new PexpireCommand(store);

        long before = System.currentTimeMillis();
        Reply r = cmd.execute(new byte[][]{bytes("k"), bytes("5000")});
        long after = System.currentTimeMillis();

        assertInstanceOf(Reply.Integer.class, r);
        assertEquals(1L, ((Reply.Integer) r).n());
        long expiry = store.getExpiry(bytes("k"));
        assertTrue(expiry >= before + 5_000 && expiry <= after + 5_000);
    }

    @Test
    void pexpireOnMissingKeyReturnsZero() {
        PexpireCommand cmd = new PexpireCommand(new Store());
        Reply r = cmd.execute(new byte[][]{bytes("k"), bytes("5000")});
        assertInstanceOf(Reply.Integer.class, r);
        assertEquals(0L, ((Reply.Integer) r).n());
    }

    @Test
    void pexpireWithZeroDeletesKey() {
        Store store = new Store();
        store.set(bytes("k"), sv("v"), 0L);
        PexpireCommand cmd = new PexpireCommand(store);

        Reply r = cmd.execute(new byte[][]{bytes("k"), bytes("0")});
        assertInstanceOf(Reply.Integer.class, r);
        assertEquals(1L, ((Reply.Integer) r).n());
        assertNull(store.get(bytes("k")));
    }

    @Test
    void pexpireWithNegativeDeletesKey() {
        Store store = new Store();
        store.set(bytes("k"), sv("v"), 0L);
        PexpireCommand cmd = new PexpireCommand(store);

        Reply r = cmd.execute(new byte[][]{bytes("k"), bytes("-1000")});
        assertInstanceOf(Reply.Integer.class, r);
        assertEquals(1L, ((Reply.Integer) r).n());
        assertNull(store.get(bytes("k")));
    }

    @Test
    void nonNumericArgumentReturnsError() {
        PexpireCommand cmd = new PexpireCommand(new Store());
        assertInstanceOf(Reply.Error.class, cmd.execute(new byte[][]{bytes("k"), bytes("abc")}));
    }

    @Test
    void wrongArityReturnsError() {
        PexpireCommand cmd = new PexpireCommand(new Store());
        assertInstanceOf(Reply.Error.class, cmd.execute(new byte[0][]));
        assertInstanceOf(Reply.Error.class, cmd.execute(new byte[][]{bytes("k")}));
        assertInstanceOf(Reply.Error.class, cmd.execute(new byte[][]{bytes("k"), bytes("5000"), bytes("x")}));
    }

    @Test
    void isWrite() {
        assertTrue(new PexpireCommand(new Store()).isWrite());
    }
}