package dev.anas.redis.command.handlers;

import dev.anas.redis.protocol.Reply;
import dev.anas.redis.store.Store;
import dev.anas.redis.store.Value;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class HsetCommandTest {

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.US_ASCII);
    }

    @Test
    void setsSingleFieldOnMissingKey() {
        Store store = new Store();
        Reply r = new HsetCommand(store).execute(new byte[][]{bytes("h"), bytes("f"), bytes("v")});
        assertInstanceOf(Reply.Integer.class, r);
        assertEquals(1L, ((Reply.Integer) r).n());
    }

    @Test
    void setsMultipleFieldsReturnsNewCount() {
        Store store = new Store();
        Reply r = new HsetCommand(store).execute(
            new byte[][]{bytes("h"), bytes("a"), bytes("1"), bytes("b"), bytes("2")});
        assertEquals(2L, ((Reply.Integer) r).n());
    }

    @Test
    void updateExistingFieldDoesNotCountAsNew() {
        Store store = new Store();
        HsetCommand cmd = new HsetCommand(store);
        cmd.execute(new byte[][]{bytes("h"), bytes("a"), bytes("1")});
        // Update 'a', add 'c' — only 'c' is new
        Reply r = cmd.execute(new byte[][]{bytes("h"), bytes("a"), bytes("99"), bytes("c"), bytes("3")});
        assertEquals(1L, ((Reply.Integer) r).n());
    }

    @Test
    void updateOverwritesValue() {
        Store store = new Store();
        HsetCommand cmd = new HsetCommand(store);
        cmd.execute(new byte[][]{bytes("h"), bytes("a"), bytes("old")});
        cmd.execute(new byte[][]{bytes("h"), bytes("a"), bytes("new")});
        Reply r = new HgetCommand(store).execute(new byte[][]{bytes("h"), bytes("a")});
        assertArrayEquals(bytes("new"), ((Reply.BulkString) r).data());
    }

    @Test
    void hsetOnWrongTypeReturnsWrongType() {
        Store store = new Store();
        store.set(bytes("h"), new Value.StringValue(bytes("v")), 0L);
        Reply r = new HsetCommand(store).execute(new byte[][]{bytes("h"), bytes("f"), bytes("v")});
        assertInstanceOf(Reply.Error.class, r);
        assertTrue(((Reply.Error) r).msg().startsWith("WRONGTYPE"));
    }

    @Test
    void wrongArityReturnsError() {
        HsetCommand cmd = new HsetCommand(new Store());
        assertInstanceOf(Reply.Error.class, cmd.execute(new byte[0][]));                                              // 0 args
        assertInstanceOf(Reply.Error.class, cmd.execute(new byte[][]{bytes("h")}));                                   // 1 arg
        assertInstanceOf(Reply.Error.class, cmd.execute(new byte[][]{bytes("h"), bytes("f")}));                       // 2 args
        assertInstanceOf(Reply.Error.class, cmd.execute(new byte[][]{bytes("h"), bytes("f"), bytes("v"), bytes("x")})); // even count (4)
    }

    @Test
    void isWrite() {
        assertTrue(new HsetCommand(new Store()).isWrite());
    }
}