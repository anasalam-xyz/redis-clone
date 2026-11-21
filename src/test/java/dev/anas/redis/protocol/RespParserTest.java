package dev.anas.redis.protocol;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

class RespParserTest {

    // ---------- helpers ----------

    /** Encodes a command as RESP: *N\r\n$len\r\nbytes\r\n ... */
    private static byte[] cmd(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        write(out, "*" + parts.length + "\r\n");
        for (byte[] p : parts) {
            write(out, "$" + p.length + "\r\n");
            out.writeBytes(p);
            write(out, "\r\n");
        }
        return out.toByteArray();
    }

    private static byte[] cmd(String... parts) {
        byte[][] raw = new byte[parts.length][];
        for (int i = 0; i < parts.length; i++) {
            raw[i] = parts[i].getBytes(StandardCharsets.UTF_8);
        }
        return cmd(raw);
    }

    private static void write(ByteArrayOutputStream out, String s) {
        out.writeBytes(s.getBytes(StandardCharsets.US_ASCII));
    }

    private static byte[] ascii(String s) {
        return s.getBytes(StandardCharsets.US_ASCII);
    }

    private static byte[] concat(byte[]... arrays) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] a : arrays) out.writeBytes(a);
        return out.toByteArray();
    }

    private static ParseResult.Parsed assertParsed(ParseResult r) {
        return assertInstanceOf(ParseResult.Parsed.class, r, "expected Parsed but got " + r);
    }

    private static void assertNeedMore(ParseResult r) {
        assertInstanceOf(ParseResult.NeedMore.class, r, "expected NeedMore but got " + r);
    }

    private static void assertError(ParseResult r) {
        assertInstanceOf(ParseResult.ParseError.class, r, "expected ParseError but got " + r);
    }

    private static List<String> strings(ParseResult.Parsed p) {
        List<String> out = new ArrayList<>();
        for (byte[] a : p.args()) out.add(new String(a, StandardCharsets.UTF_8));
        return out;
    }

    // ---------- basic parsing ----------

    @Test
    void parsesSimpleSetCommand() {
        RespParser parser = new RespParser();
        ParseResult.Parsed p = assertParsed(parser.feed(cmd("SET", "name", "anas")));
        assertEquals(List.of("SET", "name", "anas"), strings(p));
    }

    @Test
    void parsesRealRedisCliBytes() {
        // exactly what redis-cli sent for: set name anas
        byte[] wire = ascii("*3\r\n$3\r\nset\r\n$4\r\nname\r\n$4\r\nanas\r\n");
        RespParser parser = new RespParser();
        ParseResult.Parsed p = assertParsed(parser.feed(wire));
        assertEquals(List.of("set", "name", "anas"), strings(p));
    }

    @Test
    void emptyBufferNeedsMore() {
        assertNeedMore(new RespParser().next());
        assertNeedMore(new RespParser().feed(new byte[0]));
    }

    @Test
    void parsesSingleArgumentCommand() {
        RespParser parser = new RespParser();
        ParseResult.Parsed p = assertParsed(parser.feed(cmd("PING")));
        assertEquals(List.of("PING"), strings(p));
    }

    @Test
    void parsesEmptyBulkString() {
        RespParser parser = new RespParser();
        ParseResult.Parsed p = assertParsed(parser.feed(cmd("SET", "k", "")));
        assertEquals(3, p.args().length);
        assertEquals(0, p.args()[2].length);
    }

    @Test
    void bulkStringMayContainCrlfAndBinaryBytes() {
        byte[] tricky = {0, '\r', '\n', (byte) 0xFF, '$', '*', '\r', '\n', 7};
        RespParser parser = new RespParser();
        ParseResult.Parsed p = assertParsed(parser.feed(cmd("SET".getBytes(), "k".getBytes(), tricky)));
        assertArrayEquals(tricky, p.args()[2]);
    }

    @Test
    void valueWithSpacesAndJsonIsKeptIntact() {
        String json = "{\"name\": \"anas\", \"tags\": [\"a b\", \"c\\r\\nd\"]}";
        RespParser parser = new RespParser();
        ParseResult.Parsed p = assertParsed(parser.feed(cmd("SET", "user:1", json)));
        assertEquals(json, strings(p).get(2));
    }

    // ---------- partial input and TCP chunking ----------

    @Test
    void partialCommandNeedsMoreThenCompletes() {
        byte[] wire = cmd("SET", "name", "anas");
        RespParser parser = new RespParser();
        assertNeedMore(parser.feed(Arrays.copyOfRange(wire, 0, 10)));
        assertNeedMore(parser.next());                       // still incomplete, nothing consumed
        ParseResult.Parsed p = assertParsed(parser.feed(Arrays.copyOfRange(wire, 10, wire.length)));
        assertEquals(List.of("SET", "name", "anas"), strings(p));
    }

    @Test
    void incompleteLengthLinesNeedMore() {
        RespParser a = new RespParser();
        assertNeedMore(a.feed(ascii("*")));
        RespParser b = new RespParser();
        assertNeedMore(b.feed(ascii("*3")));
        RespParser c = new RespParser();
        assertNeedMore(c.feed(ascii("*3\r")));
        RespParser d = new RespParser();
        assertNeedMore(d.feed(ascii("*1\r\n$3")));
        RespParser e = new RespParser();
        assertNeedMore(e.feed(ascii("*1\r\n$3\r")));
    }

    @Test
    void bulkDataMissingTrailingCrlfNeedsMore() {
        RespParser parser = new RespParser();
        assertNeedMore(parser.feed(ascii("*1\r\n$3\r\nabc")));      // CRLF not here yet
        assertNeedMore(parser.feed(ascii("\r")));                   // only half of it
        ParseResult.Parsed p = assertParsed(parser.feed(ascii("\n")));
        assertEquals(List.of("abc"), strings(p));
    }

    @Test
    void worksWhenFedOneByteAtATime() {
        byte[] wire = cmd("SET", "name", "anas");
        RespParser parser = new RespParser();
        for (int i = 0; i < wire.length - 1; i++) {
            assertNeedMore(parser.feed(new byte[] {wire[i]}), "byte " + i);
        }
        ParseResult.Parsed p = assertParsed(parser.feed(new byte[] {wire[wire.length - 1]}));
        assertEquals(List.of("SET", "name", "anas"), strings(p));
    }

    private static void assertNeedMore(ParseResult r, String message) {
        assertInstanceOf(ParseResult.NeedMore.class, r, message);
    }

    @Test
    void worksWhenSplitAtEveryPossibleBoundary() {
        byte[] wire = cmd("SET", "key", "value with spaces");
        for (int split = 1; split < wire.length; split++) {
            RespParser parser = new RespParser();
            assertNeedMore(parser.feed(Arrays.copyOfRange(wire, 0, split)), "split at " + split);
            ParseResult.Parsed p = assertParsed(parser.feed(Arrays.copyOfRange(wire, split, wire.length)));
            assertEquals(List.of("SET", "key", "value with spaces"), strings(p), "split at " + split);
        }
    }

    // ---------- pipelining ----------

    @Test
    void pipelinedCommandsInOneChunkAreParsedInOrder() {
        byte[] wire = concat(cmd("SET", "a", "1"), cmd("GET", "a"), cmd("DEL", "a"));
        RespParser parser = new RespParser();

        assertEquals(List.of("SET", "a", "1"), strings(assertParsed(parser.feed(wire))));
        assertEquals(List.of("GET", "a"), strings(assertParsed(parser.next())));
        assertEquals(List.of("DEL", "a"), strings(assertParsed(parser.next())));
        assertNeedMore(parser.next());
    }

    @Test
    void chunkEndingInTheMiddleOfTheSecondCommand() {
        byte[] first = cmd("SET", "a", "1");
        byte[] second = cmd("GET", "a");
        byte[] wire = concat(first, second);
        RespParser parser = new RespParser();

        // first command complete, second only half there
        assertEquals(List.of("SET", "a", "1"),
                strings(assertParsed(parser.feed(Arrays.copyOfRange(wire, 0, first.length + 5)))));
        assertNeedMore(parser.next());

        parser.append(wire, first.length + 5, wire.length - first.length - 5);
        assertEquals(List.of("GET", "a"), strings(assertParsed(parser.next())));
        assertNeedMore(parser.next());
    }

    // ---------- large values ----------

    @Test
    void oneMegabyteValueArrivingInSmallChunks() {
        byte[] value = new byte[1024 * 1024];
        new Random(42).nextBytes(value);
        byte[] wire = cmd("SET".getBytes(), "big".getBytes(), value);

        RespParser parser = new RespParser();
        int chunk = 8192;
        ParseResult last = null;
        for (int off = 0; off < wire.length; off += chunk) {
            int len = Math.min(chunk, wire.length - off);
            parser.append(wire, off, len);
            last = parser.next();
            if (off + len < wire.length) {
                assertNeedMore(last, "incomplete at offset " + off);
            }
        }
        ParseResult.Parsed p = assertParsed(last);
        assertArrayEquals(value, p.args()[2]);
        assertNeedMore(parser.next());
    }

    @Test
    void parserKeepsWorkingAfterAVeryLargeValue() {
        byte[] value = new byte[2 * 1024 * 1024];
        Arrays.fill(value, (byte) 'x');
        RespParser parser = new RespParser();

        assertArrayEquals(value,
                assertParsed(parser.feed(cmd("SET".getBytes(), "big".getBytes(), value))).args()[2]);

        // buffer may have been shrunk; small commands must still work
        for (int i = 0; i < 100; i++) {
            assertEquals(List.of("GET", "k" + i), strings(assertParsed(parser.feed(cmd("GET", "k" + i)))));
        }
    }

    // ---------- long-lived connection (the buffer-growth regression) ----------

    @Test
    void manyCommandsThroughOneParserStayCorrect() {
        RespParser parser = new RespParser();
        for (int i = 0; i < 100_000; i++) {
            ParseResult.Parsed p = assertParsed(parser.feed(cmd("SET", "key:" + i, "value:" + i)));
            assertEquals("key:" + i, strings(p).get(1));
            assertEquals("value:" + i, strings(p).get(2));
        }
        assertNeedMore(parser.next());
    }

    @Test
    void randomChunkBoundariesOverALongStream() {
        Random rnd = new Random(7);
        List<List<String>> expected = new ArrayList<>();
        ByteArrayOutputStream stream = new ByteArrayOutputStream();

        for (int i = 0; i < 5_000; i++) {
            String value = "v".repeat(rnd.nextInt(300)) + i;
            expected.add(List.of("SET", "key:" + i, value));
            stream.writeBytes(cmd("SET", "key:" + i, value));
        }
        byte[] wire = stream.toByteArray();

        RespParser parser = new RespParser();
        List<List<String>> actual = new ArrayList<>();
        int off = 0;
        while (off < wire.length) {
            int len = Math.min(1 + rnd.nextInt(2000), wire.length - off);
            parser.append(wire, off, len);
            off += len;
            ParseResult r;
            while ((r = parser.next()) instanceof ParseResult.Parsed p) {
                actual.add(strings(p));
            }
            assertNeedMore(r);
        }
        assertEquals(expected, actual);
    }

    // ---------- malformed input ----------

    @Test
    void rejectsInputThatDoesNotStartWithAnArray() {
        assertError(new RespParser().feed(ascii("PING\r\n")));
        assertError(new RespParser().feed(ascii("+OK\r\n")));
    }

    @Test
    void rejectsZeroLengthArray() {
        assertError(new RespParser().feed(ascii("*0\r\n")));
    }

    @Test
    void rejectsHugeArrayLength() {
        assertError(new RespParser().feed(ascii("*2000000000\r\n")));
    }

    @Test
    void rejectsHugeBulkLength() {
        assertError(new RespParser().feed(ascii("*1\r\n$600000000\r\n")));
    }

    @Test
    void rejectsNonDigitLength() {
        assertError(new RespParser().feed(ascii("*x\r\n")));
        assertError(new RespParser().feed(ascii("*1\r\n$a\r\n")));
    }

    @Test
    void rejectsEmptyLength() {
        assertError(new RespParser().feed(ascii("*\r\n")));
        assertError(new RespParser().feed(ascii("*1\r\n$\r\n")));
    }

    @Test
    void rejectsNegativeBulkLength() {
        assertError(new RespParser().feed(ascii("*1\r\n$-1\r\n")));
    }

    @Test
    void rejectsIntegerOverflow() {
        assertError(new RespParser().feed(ascii("*99999999999\r\n")));
    }

    @Test
    void rejectsElementThatIsNotABulkString() {
        assertError(new RespParser().feed(ascii("*1\r\n:5\r\n")));
    }

    @Test
    void rejectsBulkStringWithoutTrailingCrlf() {
        assertError(new RespParser().feed(ascii("*1\r\n$3\r\nabcXY")));
    }

    @Test
    void rejectsBareCarriageReturnInLengthLine() {
        assertError(new RespParser().feed(ascii("*1\rx\n")));
    }
}