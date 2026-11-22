package dev.anas.redis.store;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.IntConsumer;

import org.junit.jupiter.api.Test;

/**
 * Needs these Store members: Store(), Store(long maxMemory), size(), deleteIfExpiresAt(byte[], long).
 * Unlimited store = new Store(). Limit counts value bytes only (strings, list elements, hash key+value).
 */
class StoreTest {

    // ---------- helpers ----------

    private static byte[] b(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    private static String s(byte[] x) {
        return x == null ? null : new String(x, StandardCharsets.UTF_8);
    }

    private static Value.StringValue str(String s) {
        return new Value.StringValue(b(s));
    }

    private static byte[][] arr(String... xs) {
        byte[][] out = new byte[xs.length][];
        for (int i = 0; i < xs.length; i++) out[i] = b(xs[i]);
        return out;
    }

    private static long past() {
        return System.currentTimeMillis() - 1000;
    }

    private static long future() {
        return System.currentTimeMillis() + 60_000;
    }

    private static <T> T ok(StoreResult<T> r) {
        if (r instanceof StoreResult.Success<T>(var value)) return value;
        return fail("expected Success but got " + r);
    }

    private static void assertWrongType(StoreResult<?> r) {
        assertInstanceOf(StoreResult.WrongType.class, r);
    }

    private static void put(Store st, String key, String value) {
        st.set(b(key), str(value), 0L);
    }

    private static String getStr(Store st, String key) {
        Value v = st.get(b(key));
        if (v == null) return null;
        return s(assertInstanceOf(Value.StringValue.class, v).bytes());
    }

    private static void rpush(Store st, String key, String... items) {
        ok(st.listPushAll(b(key), arr(items), false));
    }

    private static void hset(Store st, String key, String... pairs) {
        ok(st.hashSet(b(key), arr(pairs)));
    }

    private static List<String> strings(List<byte[]> items) {
        List<String> out = new ArrayList<>();
        for (byte[] x : items) out.add(s(x));
        return out;
    }

    private static Map<String, String> fields(Map<Key, byte[]> m) {
        Map<String, String> out = new HashMap<>();
        m.forEach((k, v) -> out.put(s(k.bytes()), s(v)));
        return out;
    }

    private static void runConcurrently(int threads, IntConsumer task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                final int id = t;
                futures.add(pool.submit(() -> task.accept(id)));
            }
            for (Future<?> f : futures) f.get(60, TimeUnit.SECONDS);   // rethrows any exception from a worker
        } finally {
            pool.shutdownNow();
        }
    }

    // ---------- strings ----------

    @Test
    void getOfMissingKeyIsNull() {
        assertNull(new Store().get(b("nope")));
    }

    @Test
    void setThenGet() {
        Store st = new Store();
        put(st, "k", "v");
        assertEquals("v", getStr(st, "k"));
    }

    @Test
    void setOverwritesThePreviousValue() {
        Store st = new Store();
        put(st, "k", "old");
        put(st, "k", "new");
        assertEquals("new", getStr(st, "k"));
        assertEquals(1, st.size());
    }

    @Test
    void emptyValueIsStored() {
        Store st = new Store();
        put(st, "k", "");
        assertEquals("", getStr(st, "k"));
    }

    @Test
    void binaryKeysAndValuesAreStoredIntact() {
        Store st = new Store();
        byte[] key = {0, '\r', '\n', (byte) 0xFF};
        byte[] val = {(byte) 0xFE, 0, '\r', '\n', '$'};
        st.set(key, new Value.StringValue(val), 0L);

        Value.StringValue sv = assertInstanceOf(Value.StringValue.class, st.get(key.clone()));
        assertArrayEquals(val, sv.bytes());
    }

    @Test
    void keysAreComparedByContentNotByArrayIdentity() {
        Store st = new Store();
        byte[] k1 = b("key");
        byte[] k2 = b("key");
        assertNotSame(k1, k2);

        st.set(k1, str("v"), 0L);
        assertNotNull(st.get(k2));
        assertTrue(st.del(k2));
    }

    @Test
    void delReturnsTrueOnceThenFalse() {
        Store st = new Store();
        put(st, "k", "v");
        assertTrue(st.del(b("k")));
        assertFalse(st.del(b("k")));
        assertNull(st.get(b("k")));
    }

    @Test
    void existsReflectsPresence() {
        Store st = new Store();
        assertFalse(st.exists(b("k")));
        put(st, "k", "v");
        assertTrue(st.exists(b("k")));
        st.del(b("k"));
        assertFalse(st.exists(b("k")));
    }

    @Test
    void sizeCountsKeysOfEveryType() {
        Store st = new Store();
        assertEquals(0, st.size());
        put(st, "s", "v");
        rpush(st, "l", "a");
        hset(st, "h", "f", "v");
        assertEquals(3, st.size());
        st.del(b("l"));
        assertEquals(2, st.size());
    }

    @Test
    void getReturnsTheStoredTypeForListsAndHashes() {
        Store st = new Store();
        rpush(st, "l", "a");
        hset(st, "h", "f", "v");
        assertInstanceOf(Value.ListValue.class, st.get(b("l")));
        assertInstanceOf(Value.HashValue.class, st.get(b("h")));
    }

    // ---------- expiry ----------

    @Test
    void newKeyHasNoExpiry() {
        Store st = new Store();
        put(st, "k", "v");
        assertEquals(0L, st.getExpiry(b("k")));
    }

    @Test
    void getExpiryOfMissingKeyIsMinusOne() {
        assertEquals(-1L, new Store().getExpiry(b("nope")));
    }

    @Test
    void expiredKeyIsInvisibleToGetAndExists() {
        Store st = new Store();
        st.set(b("k"), str("v"), past());
        assertNull(st.get(b("k")));
        assertFalse(st.exists(b("k")));
    }

    @Test
    void getExpiryOfExpiredKeyIsMinusOne() {
        Store st = new Store();
        st.set(b("k"), str("v"), past());
        assertEquals(-1L, st.getExpiry(b("k")));
    }

    @Test
    void setWithFutureExpiryStoresTheTime() {
        Store st = new Store();
        long at = future();
        st.set(b("k"), str("v"), at);
        assertEquals(at, st.getExpiry(b("k")));
        assertEquals("v", getStr(st, "k"));
    }

    @Test
    void setExpiryOnMissingKeyReturnsFalse() {
        assertFalse(new Store().setExpiry(b("nope"), future()));
    }

    @Test
    void setExpiryStoresTheTimeAndKeepsTheValue() {
        Store st = new Store();
        put(st, "k", "v");
        long at = future();
        assertTrue(st.setExpiry(b("k"), at));
        assertEquals(at, st.getExpiry(b("k")));
        assertEquals("v", getStr(st, "k"));
    }

    @Test
    void setExpiryOnExpiredKeyReturnsFalseAndKeyStaysGone() {
        Store st = new Store();
        st.set(b("k"), str("v"), past());
        assertFalse(st.setExpiry(b("k"), future()));
        assertNull(st.get(b("k")));
    }

    @Test
    void persistRemovesTheTtl() {
        Store st = new Store();
        st.set(b("k"), str("v"), future());
        assertTrue(st.persist(b("k")));
        assertEquals(0L, st.getExpiry(b("k")));
        assertEquals("v", getStr(st, "k"));
    }

    @Test
    void persistReturnsFalseWithoutTtl() {
        Store st = new Store();
        put(st, "k", "v");
        assertFalse(st.persist(b("k")));
    }

    @Test
    void persistOnMissingKeyReturnsFalse() {
        assertFalse(new Store().persist(b("nope")));
    }

    @Test
    void plainSetClearsAnExistingTtl() {
        Store st = new Store();
        st.set(b("k"), str("old"), future());
        put(st, "k", "new");
        assertEquals(0L, st.getExpiry(b("k")));
    }

    @Test
    void setKeepTtlKeepsTheExistingTtl() {
        Store st = new Store();
        long at = future();
        st.set(b("k"), str("old"), at);
        st.setKeepTtl(b("k"), str("new"));
        assertEquals(at, st.getExpiry(b("k")));
        assertEquals("new", getStr(st, "k"));
    }

    @Test
    void setKeepTtlOnMissingKeyHasNoTtl() {
        Store st = new Store();
        st.setKeepTtl(b("k"), str("v"));
        assertEquals(0L, st.getExpiry(b("k")));
        assertEquals("v", getStr(st, "k"));
    }

    @Test
    void setKeepTtlOnExpiredKeyHasNoTtl() {
        Store st = new Store();
        st.set(b("k"), str("old"), past());
        st.setKeepTtl(b("k"), str("new"));
        assertEquals(0L, st.getExpiry(b("k")));
        assertEquals("new", getStr(st, "k"));
    }

    // ---------- deleteIfExpiresAt (used by the sweeper) ----------

    @Test
    void deleteIfExpiresAtDeletesWhenTheTimeMatches() {
        Store st = new Store();
        long at = future();
        st.set(b("k"), str("v"), at);
        assertTrue(st.deleteIfExpiresAt(b("k"), at));
        assertEquals(0, st.size());
    }

    @Test
    void deleteIfExpiresAtKeepsTheKeyWhenTheTimeDiffers() {
        Store st = new Store();
        long at = future();
        st.set(b("k"), str("v"), at);
        assertFalse(st.deleteIfExpiresAt(b("k"), at - 5));
        assertEquals("v", getStr(st, "k"));
    }

    @Test
    void deleteIfExpiresAtIgnoresAMissingKey() {
        assertFalse(new Store().deleteIfExpiresAt(b("nope"), future()));
    }

    @Test
    void deleteIfExpiresAtKeepsAKeyThatWasPersisted() {
        Store st = new Store();
        long at = future();
        st.set(b("k"), str("v"), at);
        st.persist(b("k"));
        assertFalse(st.deleteIfExpiresAt(b("k"), at));
        assertEquals("v", getStr(st, "k"));
    }

    @Test
    void deleteIfExpiresAtKeepsAKeyThatWasOverwrittenWithoutTtl() {
        Store st = new Store();
        long at = future();
        st.set(b("k"), str("old"), at);
        put(st, "k", "new");
        assertFalse(st.deleteIfExpiresAt(b("k"), at));
        assertEquals("new", getStr(st, "k"));
    }

    @Test
    void deleteIfExpiresAtFreesMemory() {
        Store st = new Store(10);
        long at = future();
        st.set(b("a"), str("aaaaa"), at);
        assertTrue(st.deleteIfExpiresAt(b("a"), at));
        put(st, "b", "bbbbb");
        put(st, "c", "ccccc");
        assertNotNull(st.get(b("b")));
        assertNotNull(st.get(b("c")));
    }

    // ---------- lists ----------

    @Test
    void rpushKeepsInsertionOrder() {
        Store st = new Store();
        assertEquals(3L, ok(st.listPushAll(b("l"), arr("a", "b", "c"), false)));
        assertEquals(List.of("a", "b", "c"), strings(ok(st.listRange(b("l"), 0, -1))));
    }

    @Test
    void lpushReversesTheOrderOfItsArguments() {
        Store st = new Store();
        ok(st.listPushAll(b("l"), arr("a", "b", "c"), true));
        assertEquals(List.of("c", "b", "a"), strings(ok(st.listRange(b("l"), 0, -1))));
    }

    @Test
    void pushReturnsTheNewLength() {
        Store st = new Store();
        assertEquals(2L, ok(st.listPushAll(b("l"), arr("a", "b"), false)));
        assertEquals(3L, ok(st.listPushAll(b("l"), arr("c"), false)));
        assertEquals(3L, ok(st.listLen(b("l"))));
    }

    @Test
    void llenOfMissingKeyIsZero() {
        assertEquals(0L, ok(new Store().listLen(b("nope"))));
    }

    @Test
    void lrangeSupportsNegativeIndexes() {
        Store st = new Store();
        rpush(st, "l", "a", "b", "c", "d");
        assertEquals(List.of("c", "d"), strings(ok(st.listRange(b("l"), -2, -1))));
        assertEquals(List.of("a", "b", "c", "d"), strings(ok(st.listRange(b("l"), 0, -1))));
        assertEquals(List.of("b", "c"), strings(ok(st.listRange(b("l"), 1, 2))));
    }

    @Test
    void lrangeClampsOutOfRangeIndexes() {
        Store st = new Store();
        rpush(st, "l", "a", "b", "c");
        assertEquals(List.of("a", "b", "c"), strings(ok(st.listRange(b("l"), 0, 100))));
        assertEquals(List.of("a", "b"), strings(ok(st.listRange(b("l"), -100, 1))));
        assertEquals(List.of(), strings(ok(st.listRange(b("l"), 5, 10))));
        assertEquals(List.of(), strings(ok(st.listRange(b("l"), 2, 1))));
    }

    @Test
    void lrangeOfMissingKeyIsEmpty() {
        assertEquals(List.of(), strings(ok(new Store().listRange(b("nope"), 0, -1))));
    }

    @Test
    void popFromBothEnds() {
        Store st = new Store();
        rpush(st, "l", "a", "b", "c");
        assertEquals("a", s(ok(st.listPop(b("l"), true))));
        assertEquals("c", s(ok(st.listPop(b("l"), false))));
        assertEquals(List.of("b"), strings(ok(st.listRange(b("l"), 0, -1))));
    }

    @Test
    void popOfMissingKeyIsNull() {
        assertNull(ok(new Store().listPop(b("nope"), true)));
    }

    @Test
    void poppingTheLastElementRemovesTheKey() {
        Store st = new Store();
        rpush(st, "l", "only");
        assertEquals("only", s(ok(st.listPop(b("l"), true))));
        assertFalse(st.exists(b("l")));
        assertEquals(0, st.size());
    }

    @Test
    void listOperationsOnAStringAreWrongType() {
        Store st = new Store();
        put(st, "s", "v");
        assertWrongType(st.listPushAll(b("s"), arr("x"), false));
        assertWrongType(st.listPop(b("s"), true));
        assertWrongType(st.listLen(b("s")));
        assertWrongType(st.listRange(b("s"), 0, -1));
        assertEquals("v", getStr(st, "s"));          // untouched
    }

    @Test
    void pushToAnExpiredListStartsFresh() {
        Store st = new Store();
        rpush(st, "l", "old1", "old2");
        assertTrue(st.setExpiry(b("l"), past()));    // list is now expired

        assertEquals(1L, ok(st.listPushAll(b("l"), arr("new"), false)));
        assertEquals(List.of("new"), strings(ok(st.listRange(b("l"), 0, -1))));
        assertEquals(0L, st.getExpiry(b("l")));
    }

    // ---------- hashes ----------

    @Test
    void hsetReturnsTheNumberOfNewFields() {
        Store st = new Store();
        assertEquals(2L, ok(st.hashSet(b("h"), arr("f1", "v1", "f2", "v2"))));
        assertEquals(1L, ok(st.hashSet(b("h"), arr("f1", "changed", "f3", "v3"))));
    }

    @Test
    void hgetReturnsTheValueOrNull() {
        Store st = new Store();
        hset(st, "h", "f1", "v1");
        assertEquals("v1", s(ok(st.hashGet(b("h"), b("f1")))));
        assertNull(ok(st.hashGet(b("h"), b("nofield"))));
        assertNull(ok(st.hashGet(b("nokey"), b("f1"))));
    }

    @Test
    void overwritingAFieldUpdatesItsValue() {
        Store st = new Store();
        hset(st, "h", "f", "old");
        hset(st, "h", "f", "new");
        assertEquals("new", s(ok(st.hashGet(b("h"), b("f")))));
    }

    @Test
    void hdelReturnsTheNumberOfRemovedFields() {
        Store st = new Store();
        hset(st, "h", "f1", "v1", "f2", "v2");
        assertEquals(1L, ok(st.hashDel(b("h"), arr("f1", "missing"))));
        assertFalse(ok(st.hashExists(b("h"), b("f1"))));
        assertTrue(ok(st.hashExists(b("h"), b("f2"))));
    }

    @Test
    void deletingTheLastFieldRemovesTheKey() {
        Store st = new Store();
        hset(st, "h", "f", "v");
        assertEquals(1L, ok(st.hashDel(b("h"), arr("f"))));
        assertFalse(st.exists(b("h")));
        assertEquals(0, st.size());
    }

    @Test
    void hdelOfMissingKeyIsZero() {
        assertEquals(0L, ok(new Store().hashDel(b("nope"), arr("f"))));
    }

    @Test
    void hgetallReturnsEveryField() {
        Store st = new Store();
        hset(st, "h", "f1", "v1", "f2", "v2");
        assertEquals(Map.of("f1", "v1", "f2", "v2"), fields(ok(st.hashGetAll(b("h")))));
    }

    @Test
    void hgetallOfMissingKeyIsEmpty() {
        assertTrue(ok(new Store().hashGetAll(b("nope"))).isEmpty());
    }

    @Test
    void hgetallReturnsACopy() {
        Store st = new Store();
        hset(st, "h", "f", "v");
        ok(st.hashGetAll(b("h"))).clear();                       // mutate the returned map
        assertEquals(Map.of("f", "v"), fields(ok(st.hashGetAll(b("h")))));
    }

    @Test
    void hexistsReflectsFields() {
        Store st = new Store();
        hset(st, "h", "f", "v");
        assertTrue(ok(st.hashExists(b("h"), b("f"))));
        assertFalse(ok(st.hashExists(b("h"), b("other"))));
        assertFalse(ok(st.hashExists(b("nokey"), b("f"))));
    }

    @Test
    void hashOperationsOnAStringAreWrongType() {
        Store st = new Store();
        put(st, "s", "v");
        assertWrongType(st.hashSet(b("s"), arr("f", "v")));
        assertWrongType(st.hashGet(b("s"), b("f")));
        assertWrongType(st.hashDel(b("s"), arr("f")));
        assertWrongType(st.hashGetAll(b("s")));
        assertWrongType(st.hashExists(b("s"), b("f")));
    }

    @Test
    void hashOperationsOnAListAreWrongType() {
        Store st = new Store();
        rpush(st, "l", "a");
        assertWrongType(st.hashSet(b("l"), arr("f", "v")));
        assertWrongType(st.hashGet(b("l"), b("f")));
    }

    @Test
    void listOperationsOnAHashAreWrongType() {
        Store st = new Store();
        hset(st, "h", "f", "v");
        assertWrongType(st.listPushAll(b("h"), arr("x"), false));
        assertWrongType(st.listLen(b("h")));
    }

    // ---------- memory limit and LRU eviction ----------

    @Test
    void unlimitedStoreNeverEvicts() {
        Store st = new Store();
        for (int i = 0; i < 10_000; i++) put(st, "k" + i, "value-" + i);
        assertEquals(10_000, st.size());
        for (int i = 0; i < 10_000; i++) assertNotNull(st.get(b("k" + i)), "k" + i + " was evicted");
    }

    @Test
    void evictsTheLeastRecentlyUsedKey() {
        Store st = new Store(10);
        put(st, "a", "aaaaa");
        put(st, "b", "bbbbb");           // 10 bytes: exactly at the limit
        st.get(b("a"));                  // a is now more recent than b
        put(st, "c", "ccccc");           // 15 bytes: must evict b

        assertNull(st.get(b("b")));
        assertNotNull(st.get(b("a")));
        assertNotNull(st.get(b("c")));
    }

    @Test
    void overwritingAKeyCountsAsUsingIt() {
        Store st = new Store(10);
        put(st, "a", "aaaaa");
        put(st, "b", "bbbbb");
        put(st, "a", "AAAAA");           // a is now the most recent
        put(st, "c", "ccccc");           // evicts b

        assertNull(st.get(b("b")));
        assertEquals("AAAAA", getStr(st, "a"));
    }

    @Test
    void evictionRemovesSeveralKeysWhenNeeded() {
        Store st = new Store(10);
        put(st, "a", "aaa");
        put(st, "b", "bbb");
        put(st, "c", "ccc");             // 9 bytes
        put(st, "d", "dddddddd");        // 8 more: 17 total, so a, b and c must all go

        assertNull(st.get(b("a")));
        assertNull(st.get(b("b")));
        assertNull(st.get(b("c")));
        assertNotNull(st.get(b("d")));
    }

    @Test
    void aValueLargerThanTheLimitIsNotRetained() {
        // documents current behaviour: real Redis would return an OOM error instead
        Store st = new Store(10);
        put(st, "k", "x".repeat(20));
        assertNull(st.get(b("k")));
        assertEquals(0, st.size());
    }

    @Test
    void deleteFreesMemory() {
        Store st = new Store(10);
        put(st, "a", "aaaaa");
        put(st, "b", "bbbbb");
        st.del(b("a"));
        put(st, "c", "ccccc");
        assertNotNull(st.get(b("b")));
        assertNotNull(st.get(b("c")));
    }

    @Test
    void repeatedOverwritesDoNotLeakAccounting() {
        Store st = new Store(10);
        for (int i = 0; i < 100; i++) put(st, "a", "aaaaa");
        put(st, "b", "bbbbb");
        assertNotNull(st.get(b("a")));
        assertNotNull(st.get(b("b")));
    }

    @Test
    void overwritingWithASmallerValueFreesMemory() {
        Store st = new Store(10);
        put(st, "a", "aaaaaaaa");        // 8
        put(st, "a", "aa");              // 2
        put(st, "b", "bbbbbbbb");        // 8: total 10, fits
        assertNotNull(st.get(b("a")));
        assertNotNull(st.get(b("b")));
    }

    @Test
    void listsCountTowardsTheLimit() {
        Store st = new Store(10);
        rpush(st, "l", "aaaaa", "bbbbb");   // 10 bytes
        put(st, "x", "xxxxx");              // pushes total to 15: the list is least recent
        assertNull(st.get(b("l")));
        assertNotNull(st.get(b("x")));
    }

    @Test
    void hashesCountTowardsTheLimit() {
        Store st = new Store(10);
        hset(st, "h", "ab", "cdefg");       // 2 + 5 = 7 bytes
        put(st, "x", "xxxxx");              // 12 total: the hash is least recent
        assertNull(st.get(b("h")));
        assertNotNull(st.get(b("x")));
    }

    @Test
    void poppingFreesMemory() {
        Store st = new Store(10);
        rpush(st, "l", "aaaaa", "bbbb");    // 9 bytes
        ok(st.listPop(b("l"), true));       // frees 5
        put(st, "x", "xxxxx");              // 4 + 5 = 9, fits
        assertNotNull(st.get(b("l")));
        assertNotNull(st.get(b("x")));
    }

    @Test
    void hdelFreesMemory() {
        Store st = new Store(10);
        hset(st, "h", "a", "bbbb", "c", "dddd");   // (1+4) + (1+4) = 10 bytes
        ok(st.hashDel(b("h"), arr("a")));           // frees 5
        put(st, "x", "xxxxx");                      // 5 + 5 = 10, fits
        assertNotNull(st.get(b("h")));
        assertNotNull(st.get(b("x")));
    }

    // ---------- memory accounting when an expired entry is replaced or removed ----------
    // These four fail until the matching Store methods subtract the expired entry's size.

    @Test
    void setExpiryOnAnExpiredKeyFreesItsMemory() {
        Store st = new Store(10);
        st.set(b("a"), str("aaaaa"), past());
        assertFalse(st.setExpiry(b("a"), future()));    // expired: removed
        put(st, "b", "bbbbb");
        put(st, "c", "ccccc");                          // 10 real bytes: must all fit
        assertNotNull(st.get(b("b")));
        assertNotNull(st.get(b("c")));
    }

    @Test
    void persistOnAnExpiredKeyFreesItsMemory() {
        Store st = new Store(10);
        st.set(b("a"), str("aaaaa"), past());
        assertFalse(st.persist(b("a")));
        put(st, "b", "bbbbb");
        put(st, "c", "ccccc");
        assertNotNull(st.get(b("b")));
        assertNotNull(st.get(b("c")));
    }

    @Test
    void pushOverAnExpiredListFreesTheOldMemory() {
        Store st = new Store(10);
        rpush(st, "a", "aaaaa");
        assertTrue(st.setExpiry(b("a"), past()));
        rpush(st, "a", "xx");                           // replaces the expired list
        put(st, "b", "bbbbbbbb");                       // 2 + 8 = 10: must fit
        assertEquals(List.of("xx"), strings(ok(st.listRange(b("a"), 0, -1))));
        assertNotNull(st.get(b("b")));
    }

    @Test
    void hsetOverAnExpiredHashFreesTheOldMemory() {
        Store st = new Store(10);
        hset(st, "h", "ab", "cccc");                    // 6 bytes
        assertTrue(st.setExpiry(b("h"), past()));
        hset(st, "h", "d", "eeee");                     // 5 bytes replaces it
        assertEquals("eeee", s(ok(st.hashGet(b("h"), b("d")))));
    }

    // ---------- concurrency ----------

    @Test
    void concurrentSetsOfDistinctKeysAreAllStored() throws Exception {
        Store st = new Store();
        runConcurrently(8, id -> {
            for (int i = 0; i < 2000; i++) put(st, "t" + id + "-" + i, "v" + i);
        });
        assertEquals(16_000, st.size());
        assertEquals("v1999", getStr(st, "t3-1999"));
    }

    @Test
    void concurrentPushesToOneListLoseNothing() throws Exception {
        Store st = new Store();
        runConcurrently(8, id -> {
            for (int i = 0; i < 1000; i++) ok(st.listPushAll(b("l"), arr("x"), id % 2 == 0));
        });
        assertEquals(8000L, ok(st.listLen(b("l"))));
    }

    @Test
    void concurrentMixedOperationsDoNotThrow() throws Exception {
        Store st = new Store(5_000);
        runConcurrently(8, id -> {
            Random rnd = new Random(id);
            for (int i = 0; i < 20_000; i++) {
                byte[] key = b("k" + rnd.nextInt(100));
                switch (rnd.nextInt(9)) {
                    case 0 -> st.set(key, str("v" + i), 0L);
                    case 1 -> st.get(key);
                    case 2 -> st.del(key);
                    case 3 -> st.setExpiry(key, System.currentTimeMillis() + rnd.nextInt(5) - 2);
                    case 4 -> st.persist(key);
                    case 5 -> st.listPushAll(key, arr("x", "y"), rnd.nextBoolean());
                    case 6 -> st.listPop(key, rnd.nextBoolean());
                    case 7 -> st.hashSet(key, arr("f", "v"));
                    default -> st.hashDel(key, arr("f"));
                }
            }
        });
        assertTrue(st.size() >= 0);
    }

    @Test
    void limitedStoreStaysWithinItsLimitUnderConcurrentWrites() throws Exception {
        Store st = new Store(1_000);
        runConcurrently(8, id -> {
            for (int i = 0; i < 5000; i++) put(st, "t" + id + "-" + i, "x");   // 1 byte each
        });
        assertTrue(st.size() <= 1_000, "size was " + st.size());
    }
}