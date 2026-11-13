package dev.anas.redis.command.handlers;

import dev.anas.redis.protocol.Reply;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PingCommandTest {

    @Test
    void returnsPong() {
        Reply r = new PingCommand().execute(new byte[0][]);
        assertInstanceOf(Reply.SimpleString.class, r);
        assertEquals("PONG", ((Reply.SimpleString) r).s());
    }

    @Test
    void wrongArityReturnsError() {
        PingCommand cmd = new PingCommand();

        // 1 arg — not accepted (PING takes 0 args in our version)
        Reply r1 = cmd.execute(new byte[][]{new byte[]{'x'}});
        assertInstanceOf(Reply.Error.class, r1);

        // 2 args
        Reply r2 = cmd.execute(new byte[][]{new byte[]{'x'}, new byte[]{'y'}});
        assertInstanceOf(Reply.Error.class, r2);
    }

    @Test
    void isNotWrite() {
        assertFalse(new PingCommand().isWrite());
    }
}