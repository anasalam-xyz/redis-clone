package dev.anas.redis.persistence;

import dev.anas.redis.command.Dispatcher;
import dev.anas.redis.protocol.ParseResult;
import dev.anas.redis.protocol.RespParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class AofLoader {

    public static void replay(String path, Dispatcher dispatcher) throws IOException {
        Path file = Path.of(path);
        if (!Files.exists(file))
            return;

        byte[] bytes = Files.readAllBytes(file);
        RespParser parser = new RespParser();
        ParseResult r = parser.feed(bytes);

        while (r instanceof ParseResult.Parsed p) {
            dispatcher.dispatchWithoutAof(p.args());
            r = parser.feed(new byte[0]);
        }

        if (r instanceof ParseResult.ParseError e) {
            throw new IOException("Corrupt AOF: Parse Error " + e.message());
        }
        // NeedMore at end = clean finish
    }
}