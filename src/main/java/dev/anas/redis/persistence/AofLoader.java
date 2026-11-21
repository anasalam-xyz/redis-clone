package dev.anas.redis.persistence;

import dev.anas.redis.command.Dispatcher;
import dev.anas.redis.protocol.ParseResult;
import dev.anas.redis.protocol.RespParser;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public final class AofLoader {

    public static void replay(String path, Dispatcher dispatcher) throws IOException {
        Path file = Path.of(path);
        if (!Files.exists(file))
            return;

        RespParser parser = new RespParser();
        byte[] chunk = new byte[64 * 1024];
        try (InputStream in = Files.newInputStream(file)) {
            int n;
            while ((n = in.read(chunk)) != -1) {
                parser.append(chunk, 0, n);
                ParseResult r;
                while ((r = parser.next()) instanceof ParseResult.Parsed p) {
                    dispatcher.dispatchWithoutAof(p.args());
                }
                if (r instanceof ParseResult.ParseError e) {
                    throw new IOException("Corrupt AOF: " + e.message());
                }
            }
        }
        // leftover bytes = a truncated last command (crash mid-write). so its ignored
    }
}