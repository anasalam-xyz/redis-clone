package dev.anas.redis.protocol;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class RespParserTest {

    private static byte[] b(String s) {
        return s.getBytes(StandardCharsets.US_ASCII);
    }

    private static String str(byte[] bytes) {
        return new String(bytes, StandardCharsets.US_ASCII);
    }

    // ------------------------------------------------------------------
    // Happy path
    // ------------------------------------------------------------------

    @Test
    @DisplayName("valid SET command parses into 3 args")
    void validSetCommand() {
        RespParser parser = new RespParser();
        ParseResult result = parser.feed(
                b("*3\r\n$3\r\nset\r\n$4\r\nname\r\n$4\r\nanas\r\n"));

        assertInstanceOf(ParseResult.Parsed.class, result);
        ParseResult.Parsed parsed = (ParseResult.Parsed) result;

        assertEquals(3, parsed.args().length);
        assertEquals("set", str(parsed.args()[0]));
        assertEquals("name", str(parsed.args()[1]));
        assertEquals("anas", str(parsed.args()[2]));
    }

    @Test
    @DisplayName("empty bulk string ($0) is valid")
    void emptyBulkIsValid() {
        RespParser parser = new RespParser();
        ParseResult result = parser.feed(b("*1\r\n$0\r\n\r\n"));

        assertInstanceOf(ParseResult.Parsed.class, result);
        ParseResult.Parsed parsed = (ParseResult.Parsed) result;

        assertEquals(1, parsed.args().length);
        assertEquals(0, parsed.args()[0].length);
    }

    // ------------------------------------------------------------------
    // Chunked input
    // ------------------------------------------------------------------

    @Test
    @DisplayName("command split across two chunks completes on second feed")
    void splitAcrossChunks() {
        RespParser parser = new RespParser();

        // First chunk: incomplete
        ParseResult first = parser.feed(b("*3\r\n$3\r\nse"));
        assertInstanceOf(ParseResult.NeedMore.class, first);

        // Second chunk: rest
        ParseResult second = parser.feed(b("t\r\n$4\r\nname\r\n$4\r\nanas\r\n"));
        assertInstanceOf(ParseResult.Parsed.class, second);

        ParseResult.Parsed parsed = (ParseResult.Parsed) second;
        assertEquals(3, parsed.args().length);
        assertEquals("set", str(parsed.args()[0]));
        assertEquals("name", str(parsed.args()[1]));
        assertEquals("anas", str(parsed.args()[2]));
    }

    @Test
    @DisplayName("feed with empty chunk on empty buffer returns NeedMore")
    void emptyChunkOnEmptyBuffer() {
        RespParser parser = new RespParser();
        ParseResult result = parser.feed(new byte[0]);
        assertInstanceOf(ParseResult.NeedMore.class, result);
    }

    // ------------------------------------------------------------------
    // Malformed input → ParseError (NOT a crash)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("invalid array prefix returns ParseError")
    void invalidArrayPrefix() {
        RespParser parser = new RespParser();
        ParseResult result = parser.feed(b("?3\r\n$3\r\nset\r\n"));
        assertInstanceOf(ParseResult.ParseError.class, result);
    }

    @Test
    @DisplayName("invalid bulk prefix returns ParseError")
    void invalidBulkPrefix() {
        RespParser parser = new RespParser();
        ParseResult result = parser.feed(b("*1\r\n?3\r\nset\r\n"));
        assertInstanceOf(ParseResult.ParseError.class, result);
    }

    @Test
    @DisplayName("non-numeric array length returns ParseError")
    void nonNumericArrayLength() {
        RespParser parser = new RespParser();
        ParseResult result = parser.feed(b("*x\r\n$3\r\nset\r\n"));
        assertInstanceOf(ParseResult.ParseError.class, result);
    }

    @Test
    @DisplayName("integer overflow in array length returns ParseError, does not crash")
    void overflowArrayLength() {
        RespParser parser = new RespParser();
        // 10 digits, > Integer.MAX_VALUE
        ParseResult result = parser.feed(b("*9999999999\r\n"));
        assertInstanceOf(ParseResult.ParseError.class, result);
    }

    @Test
    @DisplayName("integer overflow in bulk length returns ParseError, does not crash")
    void overflowBulkLength() {
        RespParser parser = new RespParser();
        ParseResult result = parser.feed(b("*1\r\n$9999999999\r\n"));
        assertInstanceOf(ParseResult.ParseError.class, result);
    }

    @Test
    @DisplayName("negative bulk length returns ParseError")
    void negativeBulkLength() {
        RespParser parser = new RespParser();
        ParseResult result = parser.feed(b("*1\r\n$-1\r\n"));
        assertInstanceOf(ParseResult.ParseError.class, result);
    }

    @Test
    @DisplayName("empty line returns ParseError")
    void emptyLine() {
        RespParser parser = new RespParser();
        ParseResult result = parser.feed(b("\r\n"));
        assertInstanceOf(ParseResult.ParseError.class, result);
    }

    // ------------------------------------------------------------------
    // Known limitation: pipelining
    // ------------------------------------------------------------------

    @Test
    @DisplayName("pipelined commands: first parsed now, second recovered on next feed")
    void pipelinedTwoCommands() {
        RespParser parser = new RespParser();

        ParseResult first = parser.feed(
                b("*1\r\n$4\r\nPING\r\n*1\r\n$4\r\nPING\r\n"));
        assertInstanceOf(ParseResult.Parsed.class, first);
        assertEquals("PING",
                str(((ParseResult.Parsed) first).args()[0]));

        // Current design: second command is buffered, recovered by another feed.
        ParseResult second = parser.feed(new byte[0]);
        assertInstanceOf(ParseResult.Parsed.class, second);
        assertEquals("PING",
                str(((ParseResult.Parsed) second).args()[0]));
    }
}