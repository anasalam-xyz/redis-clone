package dev.anas.redis.command;

import dev.anas.redis.protocol.Reply;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class DispatcherTest {

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.US_ASCII);
    }

    /** A command that echoes its args back, so we can inspect what the dispatcher passed. */
    private static final class EchoArgsCommand implements Command {
        byte[][] lastArgs;
        @Override public Reply execute(byte[][] args) {
            lastArgs = args;
            return new Reply.SimpleString("done");
        }
        @Override public boolean isWrite() { return false; }
    }

    @Test
    void looksUpCommandCaseInsensitively() {
        Dispatcher d = new Dispatcher();
        EchoArgsCommand cmd = new EchoArgsCommand();
        d.register("set", cmd);

        assertInstanceOf(Reply.SimpleString.class, d.dispatch(new byte[][]{bytes("SET"), bytes("k"), bytes("v")}));
        assertInstanceOf(Reply.SimpleString.class, d.dispatch(new byte[][]{bytes("Set"), bytes("k"), bytes("v")}));
        assertInstanceOf(Reply.SimpleString.class, d.dispatch(new byte[][]{bytes("sEt"), bytes("k"), bytes("v")}));
    }

    @Test
    void stripsCommandNameBeforeCallingHandler() {
        Dispatcher d = new Dispatcher();
        EchoArgsCommand cmd = new EchoArgsCommand();
        d.register("set", cmd);

        d.dispatch(new byte[][]{bytes("SET"), bytes("k"), bytes("v")});

        assertEquals(2, cmd.lastArgs.length);
        assertArrayEquals(bytes("k"), cmd.lastArgs[0]);
        assertArrayEquals(bytes("v"), cmd.lastArgs[1]);
    }

    @Test
    void unknownCommandReturnsError() {
        Dispatcher d = new Dispatcher();
        Reply r = d.dispatch(new byte[][]{bytes("nope")});
        assertInstanceOf(Reply.Error.class, r);
        assertTrue(((Reply.Error) r).msg().contains("unknown command"));
    }

    @Test
    void emptyArgsReturnsError() {
        Dispatcher d = new Dispatcher();
        Reply r = d.dispatch(new byte[0][]);
        assertInstanceOf(Reply.Error.class, r);
    }

    @Test
    void registrationIsCaseInsensitiveOnBothSides() {
        Dispatcher d = new Dispatcher();
        d.register("PING", new EchoArgsCommand());   // uppercase registration
        // dispatch with lowercase — should work
        assertInstanceOf(Reply.SimpleString.class, d.dispatch(new byte[][]{bytes("ping")}));
    }
}