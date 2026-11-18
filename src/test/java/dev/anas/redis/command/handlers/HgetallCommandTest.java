package dev.anas.redis.command.handlers;

import dev.anas.redis.protocol.Reply;
import dev.anas.redis.store.Store;
import dev.anas.redis.store.Value;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HgetallCommandTest {

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.US_ASCII);
    }

    /** Parse the alternating field/value reply array into a map for order-independent comparison. */
    private static Map<String, String> toMap(Reply.Array arr) {
        Map<String, String> m = new HashMap<>();
        List<Reply> items = arr.items();
        assertEquals(0, items.size() % 2, "reply must have even number of elements");
        for (int i = 0; i < items.size(); i += 2) {
            String field = new String(((Reply.BulkString) items.get(i)).data(), StandardCharsets.US_ASCII);
            String value = new String(((Reply.BulkString) items.get(i + 1)).data(), StandardCharsets.US_ASCII);
            m.put(field, value);
        }
        return m;
    }

    @Test
    void returnsAllFieldsAndValues() {
        Store store = new Store();
        store.hashSet(bytes("h"),
            new byte[][]{bytes("a"), bytes("1"), bytes("b"), bytes("2"), bytes("c"), bytes("3")});
        Reply r = new HgetallCommand(store).execute(new byte[][]{bytes("h")});
        assertInstanceOf(Reply.Array.class, r);
        Map<String, String> parsed = toMap((Reply.Array) r);
        assertEquals(Map.of("a", "1", "b", "2", "c", "3"), parsed);
    }

    @Test
    void returnsEmptyArrayForMissingKey() {
        Reply r = new HgetallCommand(new Store()).execute(new byte[][]{bytes("h")});
        assertInstanceOf(Reply.Array.class, r);
        assertEquals(0, ((Reply.Array) r).items().size());
    }

    @Test
    void hgetallOnWrongTypeReturnsWrongType() {
        Store store = new Store();
        store.set(bytes("h"), new Value.StringValue(bytes("v")), 0L);
        Reply r = new HgetallCommand(store).execute(new byte[][]{bytes("h")});
        assertInstanceOf(Reply.Error.class, r);
        assertTrue(((Reply.Error) r).msg().startsWith("WRONGTYPE"));
    }

    @Test
    void wrongArityReturnsError() {
        HgetallCommand cmd = new HgetallCommand(new Store());
        assertInstanceOf(Reply.Error.class, cmd.execute(new byte[0][]));
        assertInstanceOf(Reply.Error.class, cmd.execute(new byte[][]{bytes("h"), bytes("x")}));
    }

    @Test
    void isNotWrite() {
        assertFalse(new HgetallCommand(new Store()).isWrite());
    }
}