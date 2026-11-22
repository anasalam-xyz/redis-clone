package dev.anas.redis.store;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Needs: Store.size(), Store.deleteIfExpiresAt(...), Store.setSweeper(ExpirySweeper),
 * and ExpirySweeper.schedule(byte[], long) / start() / stop().
 * Tests poll with await() instead of sleeping a fixed time, so they are not timing-flaky.
 */
class ExpirySweeperTest {

    private final List<ExpirySweeper> sweepers = new ArrayList<>();

    @AfterEach
    void stopSweepers() {
        sweepers.forEach(ExpirySweeper::stop);
        sweepers.clear();
    }

    // ---------- helpers ----------

    private static byte[] b(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    private static Value.StringValue str(String s) {
        return new Value.StringValue(b(s));
    }

    private static boolean await(BooleanSupplier cond, long ms) throws InterruptedException {
        long end = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < end) {
            if (cond.getAsBoolean()) return true;
            Thread.sleep(10);
        }
        return cond.getAsBoolean();
    }

    private static String getStr(Store st, String key) {
        Value v = st.get(b(key));
        if (v == null) return null;
        return new String(assertInstanceOf(Value.StringValue.class, v).bytes(), StandardCharsets.UTF_8);
    }

    private Store storeWithSweeper() {
        return storeWithSweeper(10);
    }

    private Store storeWithSweeper(long intervalMillis) {
        Store store = new Store();
        ExpirySweeper sweeper = new ExpirySweeper(store, intervalMillis);
        store.setSweeper(sweeper);
        sweepers.add(sweeper);
        sweeper.start();
        return store;
    }

    // ---------- the sweeper does its job ----------

    @Test
    void removesAnExpiredKeyWithoutAnyRead() throws Exception {
        Store st = storeWithSweeper();
        st.set(b("k"), str("v"), System.currentTimeMillis() + 50);

        assertTrue(await(() -> st.size() == 0, 2000));      // size() never touches the key
    }

    @Test
    void removesAKeyWhoseTtlWasSetWithExpire() throws Exception {
        Store st = storeWithSweeper();
        st.set(b("k"), str("v"), 0L);
        st.setExpiry(b("k"), System.currentTimeMillis() + 50);

        assertTrue(await(() -> st.size() == 0, 2000));
    }

    @Test
    void removesShortLivedKeysAndKeepsTheLongLivedOne() throws Exception {
        Store st = storeWithSweeper();
        long soon = System.currentTimeMillis() + 50;
        st.set(b("a"), str("v"), soon);
        st.set(b("b"), str("v"), soon + 30);
        st.set(b("c"), str("v"), soon + 60);
        st.set(b("long"), str("v"), System.currentTimeMillis() + 60_000);

        assertTrue(await(() -> st.size() == 1, 3000));
        assertEquals("v", getStr(st, "long"));
    }

    @Test
    void keysWithoutTtlAreNeverRemoved() throws Exception {
        Store st = storeWithSweeper();
        for (int i = 0; i < 100; i++) st.set(b("k" + i), str("v"), 0L);

        Thread.sleep(300);
        assertEquals(100, st.size());
    }

    @Test
    void aShorterTtlSetLaterStillExpiresOnTime() throws Exception {
        Store st = storeWithSweeper();
        st.set(b("k"), str("v"), System.currentTimeMillis() + 60_000);
        st.setExpiry(b("k"), System.currentTimeMillis() + 50);

        assertTrue(await(() -> st.size() == 0, 2000));
    }

    @Test
    void setKeepTtlDoesNotLoseTheScheduledExpiry() throws Exception {
        Store st = storeWithSweeper();
        st.set(b("k"), str("old"), System.currentTimeMillis() + 100);
        st.setKeepTtl(b("k"), str("new"));

        assertTrue(await(() -> st.size() == 0, 2000));
    }

    // ---------- stale heap records must never delete a live key ----------

    @Test
    void aLongerTtlSetLaterKeepsTheKey() throws Exception {
        Store st = storeWithSweeper();
        st.set(b("k"), str("v"), System.currentTimeMillis() + 50);
        st.setExpiry(b("k"), System.currentTimeMillis() + 60_000);

        Thread.sleep(300);                                   // the old 50 ms record has surfaced by now
        assertEquals("v", getStr(st, "k"));
    }

    @Test
    void aPlainSetAfterATtlKeepsTheKey() throws Exception {
        Store st = storeWithSweeper();
        st.set(b("k"), str("v1"), System.currentTimeMillis() + 50);
        st.set(b("k"), str("v2"), 0L);

        Thread.sleep(300);
        assertEquals("v2", getStr(st, "k"));
    }

    @Test
    void persistBeforeExpiryKeepsTheKey() throws Exception {
        Store st = storeWithSweeper();
        st.set(b("k"), str("v"), System.currentTimeMillis() + 50);
        assertTrue(st.persist(b("k")));

        Thread.sleep(300);
        assertEquals("v", getStr(st, "k"));
    }

    @Test
    void deleteBeforeExpiryIsHarmlessAndALaterKeyWithTheSameNameSurvives() throws Exception {
        Store st = storeWithSweeper();
        st.set(b("k"), str("v1"), System.currentTimeMillis() + 100);
        assertTrue(st.del(b("k")));
        assertEquals(0, st.size());

        st.set(b("k"), str("v2"), 0L);                       // same name, no TTL
        Thread.sleep(400);                                   // the old record is due now
        assertEquals("v2", getStr(st, "k"));
    }

    @Test
    void noRaceBetweenTheExpiryCheckAndTheDelete() {
        // Best-effort stress test: a sweeper that checks the TTL and deletes in two separate steps
        // can delete a key that a client has just overwritten. The atomic version never does.
        Store st = storeWithSweeper(1);
        for (int i = 0; i < 20_000; i++) {
            st.set(b("k"), str("v1"), System.currentTimeMillis() + 1);
            st.set(b("k"), str("v2"), 0L);
            assertNotNull(st.get(b("k")), "key was deleted after being overwritten, iteration " + i);
        }
        assertEquals("v2", getStr(st, "k"));
    }

    // ---------- robustness ----------

    @Test
    void sweeperSurvivesARecordForAKeyThatDoesNotExist() throws Exception {
        Store st = new Store();
        ExpirySweeper sweeper = new ExpirySweeper(st, 10);
        st.setSweeper(sweeper);
        sweepers.add(sweeper);
        sweeper.start();

        sweeper.schedule(b("ghost"), System.currentTimeMillis() - 1);   // nothing stored under this name
        Thread.sleep(100);

        st.set(b("k"), str("v"), System.currentTimeMillis() + 50);      // the sweeper must still be alive
        assertTrue(await(() -> st.size() == 0, 2000));
    }

    @Test
    void aStoppedSweeperLeavesExpiredKeysAlone() throws Exception {
        Store st = new Store();
        ExpirySweeper sweeper = new ExpirySweeper(st, 10);
        st.setSweeper(sweeper);
        sweepers.add(sweeper);
        sweeper.start();
        sweeper.stop();
        Thread.sleep(100);                                   // let the loop notice it was stopped

        st.set(b("k"), str("v"), System.currentTimeMillis() + 50);
        Thread.sleep(300);
        assertEquals(1, st.size());                          // expired, but nobody swept it
    }

    // ---------- scale ----------

    @Test
    void removesAHundredThousandKeysThatExpireAtTheSameTime() throws Exception {
        Store st = storeWithSweeper();
        long at = System.currentTimeMillis() + 300;
        for (int i = 0; i < 100_000; i++) st.set(b("k" + i), str("v"), at);

        assertTrue(await(() -> st.size() == 0, 15_000), "size was " + st.size());
    }

    @Test
    void readersDuringAMassExpiryNeverFail() throws Exception {
        Store st = storeWithSweeper();
        long at = System.currentTimeMillis() + 300;
        for (int i = 0; i < 50_000; i++) st.set(b("k" + i), str("v"), at);

        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread reader = new Thread(() -> {
            try {
                Random rnd = new Random(1);
                while (st.size() > 0) st.get(b("k" + rnd.nextInt(50_000)));
            } catch (Throwable t) {
                failure.set(t);
            }
        });
        reader.setDaemon(true);
        reader.start();
        reader.join(15_000);

        assertFalse(reader.isAlive(), "keys were not all removed in time");
        assertNull(failure.get());
    }
}