package dev.anas.redis.command.handlers;

import dev.anas.redis.protocol.Reply;
import dev.anas.redis.store.Store;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class ExpireCommandTest {

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.US_ASCII);
    }

    @Test
    void expireOnExistingKeyReturnsOneAndSetsTtl() {
        Store store = new Store();
        store.set(bytes("k"), bytes("v"), 0L);
        ExpireCommand cmd = new ExpireCommand(store);

        long before = System.currentTimeMillis();
        Reply r = cmd.execute(new byte[][]{bytes("k"), bytes("10")});
        long after = System.currentTimeMillis();

        assertInstanceOf(Reply.Integer.class, r);
        assertEquals(1L, ((Reply.Integer) r).n());

        long expiry = store.getExpiry(bytes("k"));
        assertTrue(expiry >= before + 10_000 && expiry <= after + 10_000,
                   "expiry out of expected range: " + expiry);
    }

    @Test
    void expireOnMissingKeyReturnsZero() {
        Store store = new Store();
        ExpireCommand cmd = new ExpireCommand(store);

        Reply r = cmd.execute(new byte[][]{bytes("k"), bytes("10")});

        assertInstanceOf(Reply.Integer.class, r);
        assertEquals(0L, ((Reply.Integer) r).n());
    }

    @Test
    void expireWithZeroDeletesKey() {
        Store store = new Store();
        store.set(bytes("k"), bytes("v"), 0L);
        ExpireCommand cmd = new ExpireCommand(store);

        Reply r = cmd.execute(new byte[][]{bytes("k"), bytes("0")});

        assertInstanceOf(Reply.Integer.class, r);
        assertEquals(1L, ((Reply.Integer) r).n());
        assertNull(store.get(bytes("k")));
        assertFalse(store.exists(bytes("k")));
    }

    @Test
    void expireWithNegativeDeletesKey() {
        Store store = new Store();
        store.set(bytes("k"), bytes("v"), 0L);
        ExpireCommand cmd = new ExpireCommand(store);

        Reply r = cmd.execute(new byte[][]{bytes("k"), bytes("-5")});

        assertInstanceOf(Reply.Integer.class, r);
        assertEquals(1L, ((Reply.Integer) r).n());
        assertNull(store.get(bytes("k")));
    }

    @Test
    void nonNumericArgumentReturnsError() {
        ExpireCommand cmd = new ExpireCommand(new Store());
        Reply r = cmd.execute(new byte[][]{bytes("k"), bytes("abc")});
        assertInstanceOf(Reply.Error.class, r);
    }

    @Test
    void wrongArityReturnsError() {
        ExpireCommand cmd = new ExpireCommand(new Store());
        assertInstanceOf(Reply.Error.class, cmd.execute(new byte[0][]));
        assertInstanceOf(Reply.Error.class, cmd.execute(new byte[][]{bytes("k")}));
        assertInstanceOf(Reply.Error.class, cmd.execute(new byte[][]{bytes("k"), bytes("10"), bytes("x")}));
    }

    @Test
    void isWrite() {
        assertTrue(new ExpireCommand(new Store()).isWrite());
    }
}