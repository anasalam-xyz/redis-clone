package dev.anas.redis.persistence;

import dev.anas.redis.command.Dispatcher;
import dev.anas.redis.command.handlers.SetCommand;
import dev.anas.redis.store.Store;
import dev.anas.redis.store.Value;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class AofLoaderTest {

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.US_ASCII);
    }

    @Test
    void missingFileIsNoOp(@TempDir Path tmp) throws IOException {
        Store store = new Store();
        Dispatcher d = new Dispatcher();
        d.register("set", new SetCommand(store));

        AofLoader.replay(tmp.resolve("nonexistent.aof").toString(), d);

        assertFalse(store.exists(bytes("k")));   // nothing replayed
    }

    @Test
    void replaysSingleSetCommand(@TempDir Path tmp) throws IOException {
        Path aof = tmp.resolve("test.aof");
        Files.write(aof, bytes("*3\r\n$3\r\nset\r\n$1\r\nk\r\n$1\r\nv\r\n"));

        Store store = new Store();
        Dispatcher d = new Dispatcher();
        d.register("set", new SetCommand(store));

        AofLoader.replay(aof.toString(), d);

        assertArrayEquals(bytes("v"),
            ((Value.StringValue) store.get(bytes("k"))).bytes());
    }

    @Test
    void replaysMultipleCommandsInOrder(@TempDir Path tmp) throws IOException {
        Path aof = tmp.resolve("test.aof");
        Files.write(aof, bytes(
            "*3\r\n$3\r\nset\r\n$1\r\nk\r\n$1\r\n1\r\n" +
            "*3\r\n$3\r\nset\r\n$1\r\nk\r\n$1\r\n2\r\n"));

        Store store = new Store();
        Dispatcher d = new Dispatcher();
        d.register("set", new SetCommand(store));

        AofLoader.replay(aof.toString(), d);

        // Second SET wins
        assertArrayEquals(bytes("2"),
            ((Value.StringValue) store.get(bytes("k"))).bytes());
    }

    @Test
    void corruptFileThrows(@TempDir Path tmp) throws IOException {
        Path aof = tmp.resolve("test.aof");
        Files.write(aof, bytes("X not a command\r\n"));

        Store store = new Store();
        Dispatcher d = new Dispatcher();
        d.register("set", new SetCommand(store));

        assertThrows(IOException.class, () -> AofLoader.replay(aof.toString(), d));
    }
}