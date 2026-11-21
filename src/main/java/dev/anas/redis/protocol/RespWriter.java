package dev.anas.redis.protocol;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

public final class RespWriter {
    private static final byte[] CRLF = {'\r', '\n'};
    private static final byte[] NULL_BULK = "$-1\r\n".getBytes(StandardCharsets.US_ASCII);

    private RespWriter() {}

    // Convenience for tests and anything that wants a byte[]
    public static byte[] write(Reply reply) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            writeTo(out, reply);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }

    public static void writeTo(OutputStream out, Reply reply) throws IOException {
        switch (reply) {
            case Reply.SimpleString s -> { out.write('+'); out.write(ascii(s.s())); out.write(CRLF); }
            case Reply.Error e -> { out.write('-'); out.write(ascii(e.msg())); out.write(CRLF); }
            case Reply.Integer i -> { out.write(':'); writeLong(out, i.n()); out.write(CRLF); }
            case Reply.BulkString b -> {
                out.write('$'); writeLong(out, b.data().length); out.write(CRLF);
                out.write(b.data()); out.write(CRLF);
            }
            case Reply.Null n -> out.write(NULL_BULK);
            case Reply.Array a -> {
                out.write('*'); writeLong(out, a.items().size()); out.write(CRLF);
                for (Reply item : a.items()) writeTo(out, item);
            }
        }
    }

    // A command as a RESP array of bulk strings (used by the AOF).
    public static void writeCommand(OutputStream out, byte[][] args) throws IOException {
        out.write('*'); writeLong(out, args.length); out.write(CRLF);
        for (byte[] a : args) {
            out.write('$'); writeLong(out, a.length); out.write(CRLF);
            out.write(a); out.write(CRLF);
        }
    }

    private static void writeLong(OutputStream out, long v) throws IOException {
        if (v == 0) { out.write('0'); return; }
        byte[] tmp = new byte[20];
        int i = tmp.length;
        boolean neg = v < 0;
        long n = neg ? v : -v; // negative accumulation keeps Long.MIN_VALUE safe
        while (n != 0) { tmp[--i] = (byte) ('0' - (n % 10)); n /= 10; }
        if (neg) tmp[--i] = '-';
        out.write(tmp, i, tmp.length - i);
    }

    private static byte[] ascii(String s) { return s.getBytes(StandardCharsets.US_ASCII); }
}