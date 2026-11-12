package dev.anas.redis.protocol;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class RespParserTest {

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.US_ASCII);
    }

    @Test
    void parsesFullCommandInOneChunk() {
        RespParser p = new RespParser();
        ParseResult r = p.feed(bytes("*3\r\n$3\r\nset\r\n$4\r\nname\r\n$4\r\nanas\r\n"));

        assertInstanceOf(ParseResult.Parsed.class, r);
        byte[][] args = ((ParseResult.Parsed) r).args();
        assertEquals(3, args.length);
        assertArrayEquals(bytes("set"),  args[0]);
        assertArrayEquals(bytes("name"), args[1]);
        assertArrayEquals(bytes("anas"), args[2]);
    }

    @Test
    void handlesSplitRead() {
        RespParser p = new RespParser();
        ParseResult r1 = p.feed(bytes("*3\r\n$3\r\nset\r\n$4\r\nna"));
        assertInstanceOf(ParseResult.NeedMore.class, r1);

        ParseResult r2 = p.feed(bytes("me\r\n$4\r\nanas\r\n"));
        assertInstanceOf(ParseResult.Parsed.class, r2);
        byte[][] args = ((ParseResult.Parsed) r2).args();
        assertEquals(3, args.length);
        assertArrayEquals(bytes("name"), args[1]);
        assertArrayEquals(bytes("anas"), args[2]);
    }

    @Test
    void handlesPipelinedCommands() {
        RespParser p = new RespParser();
        ParseResult r1 = p.feed(bytes("*1\r\n$4\r\nping\r\n*1\r\n$4\r\nping\r\n"));
        assertInstanceOf(ParseResult.Parsed.class, r1);
        assertArrayEquals(bytes("ping"), ((ParseResult.Parsed) r1).args()[0]);

        ParseResult r2 = p.feed(new byte[0]);      // no new bytes, drain the buffer
        assertInstanceOf(ParseResult.Parsed.class, r2);
        assertArrayEquals(bytes("ping"), ((ParseResult.Parsed) r2).args()[0]);
    }

    @Test
    void handlesBinaryPayloadContainingCrlf() {
        byte[] payload = {'a', '\r', '\n', 'b', (byte) 0xFF};
        // *3\r\n$3\r\nset\r\n$1\r\nk\r\n$5\r\n<5 bytes>\r\n
        byte[] chunk = concat(
            bytes("*3\r\n$3\r\nset\r\n$1\r\nk\r\n$5\r\n"),
            payload,
            bytes("\r\n")
        );

        RespParser p = new RespParser();
        ParseResult r = p.feed(chunk);
        assertInstanceOf(ParseResult.Parsed.class, r);
        byte[][] args = ((ParseResult.Parsed) r).args();
        assertArrayEquals(payload, args[2]);
    }

    @Test
    void handlesEmptyBulkString() {
        RespParser p = new RespParser();
        ParseResult r = p.feed(bytes("*3\r\n$3\r\nset\r\n$1\r\nk\r\n$0\r\n\r\n"));
        assertInstanceOf(ParseResult.Parsed.class, r);
        byte[][] args = ((ParseResult.Parsed) r).args();
        assertEquals(3, args.length);
        assertEquals(0, args[2].length);
    }

    private static byte[] concat(byte[]... parts) {
        int n = 0;
        for (byte[] part : parts) n += part.length;
        byte[] out = new byte[n];
        int off = 0;
        for (byte[] part : parts) { System.arraycopy(part, 0, out, off, part.length); off += part.length; }
        return out;
    }
}