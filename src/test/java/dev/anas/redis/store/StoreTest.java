package dev.anas.redis.store;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class StoreTest {

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.US_ASCII);
    }

    private static Value.StringValue sv(String s) {
        return new Value.StringValue(bytes(s));
    }

    private static byte[] bytesOf(Value v) {
        return ((Value.StringValue) v).bytes();
    }

    @Test
    void setThenGetReturnsValue() {
        Store s = new Store();
        s.set(bytes("name"), sv("anas"), 0L);
        assertArrayEquals(bytes("anas"), bytesOf(s.get(bytes("name"))));
    }

    @Test
    void getMissingReturnsNull() {
        Store s = new Store();
        assertNull(s.get(bytes("nope")));
    }

    @Test
    void overwriteReplacesValue() {
        Store s = new Store();
        s.set(bytes("k"), sv("old"), 0L);
        s.set(bytes("k"), sv("new"), 0L);
        assertArrayEquals(bytes("new"), bytesOf(s.get(bytes("k"))));
    }

    @Test
    void delReturnsTrueThenFalse() {
        Store s = new Store();
        s.set(bytes("k"), sv("v"), 0L);
        assertTrue(s.del(bytes("k")));
        assertFalse(s.del(bytes("k")));
        assertNull(s.get(bytes("k")));
    }

    @Test
    void existsReflectsPresence() {
        Store s = new Store();
        assertFalse(s.exists(bytes("k")));
        s.set(bytes("k"), sv("v"), 0L);
        assertTrue(s.exists(bytes("k")));
        s.del(bytes("k"));
        assertFalse(s.exists(bytes("k")));
    }

    @Test
    void expiredKeyIsInvisibleToGetAndExists() throws InterruptedException {
        Store s = new Store();
        s.set(bytes("k"), sv("v"), System.currentTimeMillis() + 50);
        assertNotNull(s.get(bytes("k")));
        Thread.sleep(100);
        assertNull(s.get(bytes("k")));
        assertFalse(s.exists(bytes("k")));
    }

    @Test
    void binaryKeyAndValueRoundTrip() {
        Store s = new Store();
        byte[] key   = {'k', 0x00, (byte) 0xFF, '\r', '\n'};
        byte[] value = {0x00, (byte) 0x80, (byte) 0xFF, 'x'};
        s.set(key, new Value.StringValue(value), 0L);
        assertArrayEquals(value, bytesOf(s.get(key)));
    }

    @Test
    void setExpiryOnMissingKeyReturnsFalse() {
        Store s = new Store();
        assertFalse(s.setExpiry(bytes("k"), System.currentTimeMillis() + 10_000));
    }

    @Test
    void setExpiryOnExistingKeyStoresTtl() {
        Store s = new Store();
        s.set(bytes("k"), sv("v"), 0L);
        long at = System.currentTimeMillis() + 10_000;

        assertTrue(s.setExpiry(bytes("k"), at));
        assertEquals(at, s.getExpiry(bytes("k")));
    }

    @Test
    void getExpiryReturnsMinusOneForMissingKey() {
        Store s = new Store();
        assertEquals(-1L, s.getExpiry(bytes("k")));
    }

    @Test
    void getExpiryReturnsZeroForNoTtl() {
        Store s = new Store();
        s.set(bytes("k"), sv("v"), 0L);
        assertEquals(0L, s.getExpiry(bytes("k")));
    }

    @Test
    void persistRemovesTtl() {
        Store s = new Store();
        s.set(bytes("k"), sv("v"), 0L);
        s.setExpiry(bytes("k"), System.currentTimeMillis() + 10_000);

        assertTrue(s.persist(bytes("k")));
        assertEquals(0L, s.getExpiry(bytes("k")));
    }

    @Test
    void persistOnKeyWithoutTtlReturnsFalse() {
        Store s = new Store();
        s.set(bytes("k"), sv("v"), 0L);
        assertFalse(s.persist(bytes("k")));
    }

    @Test
    void persistOnMissingKeyReturnsFalse() {
        Store s = new Store();
        assertFalse(s.persist(bytes("k")));
    }

    @Test
    void sweepRemovesExpiredKeys() throws InterruptedException {
        Store s = new Store();
        s.set(bytes("live"), sv("v"), 0L);
        s.set(bytes("dead"), sv("v"), System.currentTimeMillis() + 50);

        Thread.sleep(100);
        int swept = s.sweepExpired();

        assertEquals(1, swept);
        assertNotNull(s.get(bytes("live")));
        assertNull(s.get(bytes("dead")));
    }

    @Test
    void sweepWithNoExpiredKeysReturnsZero() {
        Store s = new Store();
        s.set(bytes("live"), sv("v"), 0L);
        assertEquals(0, s.sweepExpired());
        assertNotNull(s.get(bytes("live")));
    }

    @Test
    void sweepRemovesMultipleExpiredKeysInOnePass() throws InterruptedException {
        Store s = new Store();
        for (int i = 0; i < 5; i++) {
            s.set(bytes("k" + i), sv("v"), System.currentTimeMillis() + 50);
        }
        Thread.sleep(100);
        assertEquals(5, s.sweepExpired());
    }
}