package dev.anas.redis.store;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class StoreTest {

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.US_ASCII);
    }

    @Test
    void setThenGetReturnsValue() {
        Store s = new Store();
        s.set(bytes("name"), bytes("anas"), 0L);
        assertArrayEquals(bytes("anas"), s.get(bytes("name")));
    }

    @Test
    void getMissingReturnsNull() {
        Store s = new Store();
        assertNull(s.get(bytes("nope")));
    }

    @Test
    void overwriteReplacesValue() {
        Store s = new Store();
        s.set(bytes("k"), bytes("old"), 0L);
        s.set(bytes("k"), bytes("new"), 0L);
        assertArrayEquals(bytes("new"), s.get(bytes("k")));
    }

    @Test
    void delReturnsTrueThenFalse() {
        Store s = new Store();
        s.set(bytes("k"), bytes("v"), 0L);
        assertTrue(s.del(bytes("k")));
        assertFalse(s.del(bytes("k")));
        assertNull(s.get(bytes("k")));
    }

    @Test
    void existsReflectsPresence() {
        Store s = new Store();
        assertFalse(s.exists(bytes("k")));
        s.set(bytes("k"), bytes("v"), 0L);
        assertTrue(s.exists(bytes("k")));
        s.del(bytes("k"));
        assertFalse(s.exists(bytes("k")));
    }

    @Test
    void expiredKeyIsInvisibleToGetAndExists() throws InterruptedException {
        Store s = new Store();
        s.set(bytes("k"), bytes("v"), System.currentTimeMillis() + 50);
        assertNotNull(s.get(bytes("k")));       // before expiry
        Thread.sleep(100);
        assertNull(s.get(bytes("k")));          // after expiry — lazy deletion
        assertFalse(s.exists(bytes("k")));      // exists respects expiry
    }

    @Test
    void binaryKeyAndValueRoundTrip() {
        Store s = new Store();
        byte[] key   = {'k', 0x00, (byte) 0xFF, '\r', '\n'};
        byte[] value = {0x00, (byte) 0x80, (byte) 0xFF, 'x'};
        s.set(key, value, 0L);
        assertArrayEquals(value, s.get(key));
    }
}