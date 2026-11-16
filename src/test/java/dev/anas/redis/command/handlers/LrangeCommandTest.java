package dev.anas.redis.command.handlers;

import dev.anas.redis.protocol.Reply;
import dev.anas.redis.store.Store;
import dev.anas.redis.store.Value;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class LrangeCommandTest {

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.US_ASCII);
    }

    private static Store storeWithList(String... elements) {
        Store store = new Store();
        byte[][] arr = new byte[elements.length][];
        for (int i = 0; i < elements.length; i++) arr[i] = bytes(elements[i]);
        store.listPushAll(bytes("k"), arr, false);
        return store;
    }

    private static void assertElements(Reply.Array arr, String... expected) {
        assertEquals(expected.length, arr.items().size());
        for (int i = 0; i < expected.length; i++) {
            assertArrayEquals(bytes(expected[i]),
                ((Reply.BulkString) arr.items().get(i)).data());
        }
    }

    @Test
    void fullRangeReturnsAll() {
        Store store = storeWithList("a", "b", "c", "d", "e");
        Reply r = new LrangeCommand(store).execute(
            new byte[][]{bytes("k"), bytes("0"), bytes("-1")});
        assertInstanceOf(Reply.Array.class, r);
        assertElements((Reply.Array) r, "a", "b", "c", "d", "e");
    }

    @Test
    void positiveRangeReturnsSlice() {
        Store store = storeWithList("a", "b", "c", "d", "e");
        Reply r = new LrangeCommand(store).execute(
            new byte[][]{bytes("k"), bytes("1"), bytes("3")});
        assertElements((Reply.Array) r, "b", "c", "d");
    }

    @Test
    void negativeIndicesCountFromEnd() {
        Store store = storeWithList("a", "b", "c", "d", "e");
        Reply r = new LrangeCommand(store).execute(
            new byte[][]{bytes("k"), bytes("-2"), bytes("-1")});
        assertElements((Reply.Array) r, "d", "e");
    }

    @Test
    void outOfRangeClampsToBounds() {
        Store store = storeWithList("a", "b", "c", "d", "e");
        Reply r = new LrangeCommand(store).execute(
            new byte[][]{bytes("k"), bytes("-100"), bytes("100")});
        assertElements((Reply.Array) r, "a", "b", "c", "d", "e");
    }

    @Test
    void startGreaterThanStopReturnsEmpty() {
        Store store = storeWithList("a", "b", "c", "d", "e");
        Reply r = new LrangeCommand(store).execute(
            new byte[][]{bytes("k"), bytes("3"), bytes("2")});
        assertElements((Reply.Array) r);   // no expected elements
    }

    @Test
    void stopVeryNegativeReturnsEmpty() {
        Store store = storeWithList("a", "b", "c", "d", "e");
        Reply r = new LrangeCommand(store).execute(
            new byte[][]{bytes("k"), bytes("0"), bytes("-100")});
        assertElements((Reply.Array) r);
    }

    @Test
    void missingKeyReturnsEmptyArray() {
        Reply r = new LrangeCommand(new Store()).execute(
            new byte[][]{bytes("k"), bytes("0"), bytes("-1")});
        assertInstanceOf(Reply.Array.class, r);
        assertEquals(0, ((Reply.Array) r).items().size());
    }

    @Test
    void wrongTypeReturnsWrongType() {
        Store store = new Store();
        store.set(bytes("k"), new Value.StringValue(bytes("v")), 0L);
        Reply r = new LrangeCommand(store).execute(
            new byte[][]{bytes("k"), bytes("0"), bytes("-1")});
        assertInstanceOf(Reply.Error.class, r);
        assertTrue(((Reply.Error) r).msg().startsWith("WRONGTYPE"));
    }

    @Test
    void nonIntegerIndexReturnsError() {
        LrangeCommand cmd = new LrangeCommand(new Store());
        Reply r = cmd.execute(new byte[][]{bytes("k"), bytes("abc"), bytes("-1")});
        assertInstanceOf(Reply.Error.class, r);
        assertTrue(((Reply.Error) r).msg().contains("integer"));
    }

    @Test
    void wrongArityReturnsError() {
        LrangeCommand cmd = new LrangeCommand(new Store());
        assertInstanceOf(Reply.Error.class, cmd.execute(new byte[0][]));
        assertInstanceOf(Reply.Error.class, cmd.execute(new byte[][]{bytes("k")}));
        assertInstanceOf(Reply.Error.class, cmd.execute(new byte[][]{bytes("k"), bytes("0")}));
        assertInstanceOf(Reply.Error.class, cmd.execute(
            new byte[][]{bytes("k"), bytes("0"), bytes("1"), bytes("x")}));
    }

    @Test
    void isNotWrite() {
        assertFalse(new LrangeCommand(new Store()).isWrite());
    }
}