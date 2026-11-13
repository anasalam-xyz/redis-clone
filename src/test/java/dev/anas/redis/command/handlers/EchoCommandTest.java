package dev.anas.redis.command.handlers;

import dev.anas.redis.protocol.Reply;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class EchoCommandTest {

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.US_ASCII);
    }

    @Test
    void echoesArgumentAsBulkString() {
        Reply r = new EchoCommand().execute(new byte[][]{bytes("hello")});
        assertInstanceOf(Reply.BulkString.class, r);
        assertArrayEquals(bytes("hello"), ((Reply.BulkString) r).data());
    }

    @Test
    void echoesBinaryPayloadUnchanged() {
        // The critical binary-safety test: 0x00, 0xFF, \r, \n, 0x80 must come back byte-identical.
        byte[] payload = {0x00, (byte) 0xFF, '\r', '\n', (byte) 0x80};
        Reply r = new EchoCommand().execute(new byte[][]{payload});
        assertInstanceOf(Reply.BulkString.class, r);
        assertArrayEquals(payload, ((Reply.BulkString) r).data());
    }

    @Test
    void wrongArityReturnsError() {
        EchoCommand cmd = new EchoCommand();
        assertInstanceOf(Reply.Error.class, cmd.execute(new byte[0][]));               // 0 args
        assertInstanceOf(Reply.Error.class, cmd.execute(new byte[][]{bytes("a"), bytes("b")})); // 2 args
    }

    @Test
    void isNotWrite() {
        assertFalse(new EchoCommand().isWrite());
    }
}