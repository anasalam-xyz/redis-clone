package dev.anas.redis.protocol;

import java.util.Arrays;

public final class RespParser {
    private static final int MAX_ARGS = 1_048_576;
    private static final int MAX_BULK = 512 * 1024 * 1024;
    private static final int INITIAL_CAPACITY = 16 * 1024;
    private static final int MAX_RETAINED = 1024 * 1024;
    private static final ParseResult NEED_MORE = new ParseResult.NeedMore();

    private byte[] buf = new byte[INITIAL_CAPACITY];
    private int pos = 0;      // next unread byte
    private int limit = 0;    // end of valid data
    private int cursor = 0;   // scratch position while parsing one command

    public ParseResult feed(byte[] chunk) {
        append(chunk, 0, chunk.length);
        return next();
    }

    public void append(byte[] chunk, int off, int len) {
        if (pos == limit) {  // everything consumed: restart at 0
            pos = limit = 0;
            if (buf.length > MAX_RETAINED) buf = new byte[INITIAL_CAPACITY];
        } else if (limit + len > buf.length) {        
            System.arraycopy(buf, pos, buf, 0, limit - pos);
            limit -= pos;
            pos = 0;
        }
        if (limit + len > buf.length) {        
            long want = Math.max((long) buf.length * 2, (long) limit + len);
            buf = Arrays.copyOf(buf, (int) Math.min(want, Integer.MAX_VALUE - 8));
        }
        System.arraycopy(chunk, off, buf, limit, len);
        limit += len;
    }

    // Parses the next complete command from the buffer, or returns NeedMore.
    public ParseResult next() {
        if (pos >= limit) return NEED_MORE;
        try {
            if (buf[pos] != '*') {
                return new ParseResult.ParseError("Expected array prefix '*', got: " + (char) buf[pos]);
            }
            cursor = pos + 1;
            int numArgs = readLength();
            if (numArgs < 0) return NEED_MORE;
            if (numArgs == 0 || numArgs > MAX_ARGS) {
                return new ParseResult.ParseError("Invalid multibulk length");
            }

            byte[][] args = new byte[numArgs][];
            for (int i = 0; i < numArgs; i++) {
                if (cursor >= limit) return NEED_MORE;
                if (buf[cursor] != '$') {
                    return new ParseResult.ParseError("Expected string prefix '$', got: " + (char) buf[cursor]);
                }
                cursor++;
                int len = readLength();
                if (len < 0) return NEED_MORE;
                if (len > MAX_BULK) return new ParseResult.ParseError("Invalid bulk length");
                if ((long) limit - cursor < (long) len + 2) return NEED_MORE;
                if (buf[cursor + len] != '\r' || buf[cursor + len + 1] != '\n') {
                    return new ParseResult.ParseError("Expected CRLF after bulk string");
                }
                args[i] = Arrays.copyOfRange(buf, cursor, cursor + len);
                cursor += len + 2;
            }
            pos = cursor; // commit only when the whole command was parsed
            return new ParseResult.Parsed(args);
        } catch (NumberFormatException e) {
            return new ParseResult.ParseError(e.getMessage());
        }
    }

    // Reads "<digits>\r\n" at cursor. Returns the value, or -1 if the line is incomplete.
    private int readLength() {
        int i = cursor;
        int result = 0;
        int digits = 0;
        while (i < limit) {
            byte b = buf[i];
            if (b == '\r') {
                if (i + 1 >= limit) return -1;
                if (buf[i + 1] != '\n') throw new NumberFormatException("Expected \\n after \\r");
                if (digits == 0) throw new NumberFormatException("Empty integer");
                cursor = i + 2;
                return result;
            }
            if (b < '0' || b > '9') throw new NumberFormatException("Invalid integer");
            if (result > (Integer.MAX_VALUE - (b - '0')) / 10) {
                throw new NumberFormatException("Integer overflow");
            }
            result = result * 10 + (b - '0');
            digits++;
            i++;
        }
        return -1;
    }
}