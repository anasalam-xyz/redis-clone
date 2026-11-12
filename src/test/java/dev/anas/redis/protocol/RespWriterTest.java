package dev.anas.redis.protocol;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RespWriterTest {

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.US_ASCII);
    }

    @Test
    void writesSimpleString() {
        assertArrayEquals(bytes("+OK\r\n"), RespWriter.write(new Reply.SimpleString("OK")));
    }

    @Test
    void writesError() {
        assertArrayEquals(bytes("-ERR unknown command\r\n"),
                          RespWriter.write(new Reply.Error("ERR unknown command")));
    }

    @Test
    void writesInteger() {
        assertArrayEquals(bytes(":42\r\n"), RespWriter.write(new Reply.Integer(42)));
    }

    @Test
    void writesNegativeInteger() {
        assertArrayEquals(bytes(":-1\r\n"), RespWriter.write(new Reply.Integer(-1)));
    }

    @Test
    void writesBulkString() {
        assertArrayEquals(bytes("$5\r\nhello\r\n"),
                          RespWriter.write(new Reply.BulkString(bytes("hello"))));
    }

    @Test
    void writesEmptyBulkString() {
        assertArrayEquals(bytes("$0\r\n\r\n"),
                          RespWriter.write(new Reply.BulkString(new byte[0])));
    }

    @Test
    void writesNullBulk() {
        assertArrayEquals(bytes("$-1\r\n"), RespWriter.write(new Reply.Null()));
    }

    @Test
    void writesArrayOfIntegers() {
        Reply r = new Reply.Array(List.of(new Reply.Integer(1), new Reply.Integer(2)));
        assertArrayEquals(bytes("*2\r\n:1\r\n:2\r\n"), RespWriter.write(r));
    }

    @Test
    void writesEmptyArray() {
        assertArrayEquals(bytes("*0\r\n"),
                          RespWriter.write(new Reply.Array(List.of())));
    }

    @Test
    void writesNestedArray() {
        // *2\r\n  *1\r\n:1\r\n  *2\r\n:2\r\n:3\r\n
        Reply inner1 = new Reply.Array(List.of(new Reply.Integer(1)));
        Reply inner2 = new Reply.Array(List.of(new Reply.Integer(2), new Reply.Integer(3)));
        Reply outer  = new Reply.Array(List.of(inner1, inner2));
        assertArrayEquals(bytes("*2\r\n*1\r\n:1\r\n*2\r\n:2\r\n:3\r\n"),
                          RespWriter.write(outer));
    }

    @Test
    void writesArrayContainingNullBulk() {
        // LRANGE of a missing key returns *-1 in real Redis; a mixed array with null
        // is what MGET returns for missing keys.
        Reply r = new Reply.Array(List.of(new Reply.BulkString(bytes("a")), new Reply.Null()));
        assertArrayEquals(bytes("*2\r\n$1\r\na\r\n$-1\r\n"), RespWriter.write(r));
    }

    @Test
    void bulkStringLengthIsByteCountNotCharCount() {
        // "héllo" is 5 chars but 6 bytes in UTF-8 ('é' = 0xC3 0xA9)
        byte[] data = "héllo".getBytes(StandardCharsets.UTF_8);
        byte[] out = RespWriter.write(new Reply.BulkString(data));

        // The header must say $6 (bytes), not $5 (chars).
        byte[] header = Arrays.copyOfRange(out, 0, 4);
        assertArrayEquals(bytes("$6\r\n"), header);

        // The payload must be the original bytes, byte-for-byte.
        byte[] payload = Arrays.copyOfRange(out, 4, 4 + data.length);
        assertArrayEquals(data, payload);
    }

    @Test
    void bulkStringPayloadIsNotMangledForBinary() {
        // Null byte, high-bit byte, CR, LF, and a byte that's invalid UTF-8 on its own.
        byte[] data = {0x00, (byte) 0xFF, '\r', '\n', (byte) 0x80};
        byte[] out = RespWriter.write(new Reply.BulkString(data));

        // Header "$5\r\n" is 4 bytes; then the 5 payload bytes; then "\r\n".
        byte[] header = Arrays.copyOfRange(out, 0, 4);
        assertArrayEquals(bytes("$5\r\n"), header);

        byte[] payload = Arrays.copyOfRange(out, 4, 4 + data.length);
        assertArrayEquals(data, payload);

        byte[] trailer = Arrays.copyOfRange(out, 4 + data.length, out.length);
        assertArrayEquals(bytes("\r\n"), trailer);
    }
}