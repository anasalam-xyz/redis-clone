package dev.anas.redis.protocol;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

public final class RespWriter {

    private RespWriter() {}

    // Serialize a Reply to RESP2 bytes.
    public static byte[] write(Reply reply) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeTo(out, reply);
        return out.toByteArray();
    }

    private static void writeTo(ByteArrayOutputStream out, Reply reply) {
        switch (reply) {
            case Reply.SimpleString s -> {
                out.writeBytes(ascii("+"));
                out.writeBytes(ascii(s.s()));
                out.writeBytes(ascii("\r\n"));
            }
            case Reply.Error e -> {
                out.writeBytes(ascii("-"));
                out.writeBytes(ascii(e.msg()));
                out.writeBytes(ascii("\r\n"));
            }
            case Reply.Integer i -> {
                out.writeBytes(ascii(":" + i.n() + "\r\n"));
            }
            case Reply.BulkString b -> {
                out.writeBytes(ascii("$" + b.data().length + "\r\n"));
                out.writeBytes(b.data());
                out.writeBytes(ascii("\r\n"));
            }
            case Reply.Null n -> {
                out.writeBytes(ascii("$-1\r\n"));
            }
            case Reply.Array a -> {
                out.writeBytes(ascii("*" + a.items().size() + "\r\n"));
                for (Reply item : a.items()) {
                    writeTo(out, item);
                }
            }
        }
    }

    private static byte[] ascii(String s) {
        // Encode as ASCII. 
        // Non-ASCII chars are replaced with '?' — per RESP, 
        // simple strings and errors must be ASCII-only.
        
        return s.getBytes(StandardCharsets.US_ASCII);
    }
}