package dev.anas.redis.persistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class AofWriterTest {

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.US_ASCII);
    }

    @Test
    void appendWritesCommandBytesToFile(@TempDir Path tmp) throws IOException {
        Path aof = tmp.resolve("test.aof");

        try (AofWriter writer = new AofWriter(aof.toString())) {
            writer.append(new byte[][] { bytes("set"), bytes("k"), bytes("v") });
        }

        byte[] contents = Files.readAllBytes(aof);
        assertArrayEquals(bytes("*3\r\n$3\r\nset\r\n$1\r\nk\r\n$1\r\nv\r\n"), contents);
    }

    @Test
    void closeViaTryWithResources(@TempDir Path tmp) throws IOException {
        Path aof = tmp.resolve("test.aof");

        try (AofWriter writer = new AofWriter(aof.toString())) {
            writer.append(new byte[][] { bytes("ping") });
        }

        assertTrue(Files.size(aof) > 0);
    }

    @Test
    void fsyncDoesNotCorruptFile(@TempDir Path tmp) throws IOException {
        Path aof = tmp.resolve("test.aof");

        try (AofWriter writer = new AofWriter(aof.toString())) {
            writer.append(new byte[][] { bytes("set"), bytes("a"), bytes("1") });
            writer.fsync();
            writer.append(new byte[][] { bytes("set"), bytes("b"), bytes("2") });
            writer.fsync();
        }

        String expected = "*3\r\n$3\r\nset\r\n$1\r\na\r\n$1\r\n1\r\n" +
                "*3\r\n$3\r\nset\r\n$1\r\nb\r\n$1\r\n2\r\n";
        assertArrayEquals(bytes(expected), Files.readAllBytes(aof));
    }

    @Test
    void createsParentDirectories(@TempDir Path tmp) throws IOException {
        Path aof = tmp.resolve("nested/dir/test.aof");

        try (AofWriter writer = new AofWriter(aof.toString())) {
            writer.append(new byte[][] { bytes("ping") });
        }

        assertTrue(Files.exists(aof));
    }

    @Test
    void flushThreadPersistsWritesWithoutExplicitFlush(@TempDir Path tmp) throws Exception {
        Path aof = tmp.resolve("test.aof");
        AofWriter writer = new AofWriter(aof.toString());

        writer.append(new byte[][] { bytes("set"), bytes("k"), bytes("v") });
        Thread.sleep(1500); // wait for the 1-second flush tick

        // Read the file WITHOUT closing the writer
        byte[] contents = Files.readAllBytes(aof);
        assertArrayEquals(bytes("*3\r\n$3\r\nset\r\n$1\r\nk\r\n$1\r\nv\r\n"), contents);

        writer.close();
    }
}