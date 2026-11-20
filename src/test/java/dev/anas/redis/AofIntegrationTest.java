package dev.anas.redis;

import dev.anas.redis.command.Dispatcher;
import dev.anas.redis.command.handlers.*;
import dev.anas.redis.persistence.AofLoader;
import dev.anas.redis.persistence.AofWriter;
import dev.anas.redis.server.Server;
import dev.anas.redis.store.Store;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AofIntegrationTest {

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.US_ASCII);
    }

    private static int freePort() throws IOException {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        }
    }

    private static Dispatcher buildDispatcher(Store store, AofWriter aof) {
        Dispatcher d = new Dispatcher(aof);
        d.register("set", new SetCommand(store));
        d.register("get", new GetCommand(store));
        return d;
    }

    private static void waitForPort(int port, long ms) throws Exception {
        long deadline = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < deadline) {
            try (Socket probe = new Socket("localhost", port)) {
                return;
            } catch (IOException e) {
                Thread.sleep(20);
            }
        }
        throw new IllegalStateException("server didn't start");
    }

    @Test
    void dataSurvivesRestartAndAofDoesNotGrow(@TempDir Path tmp) throws Exception {
        Path aofPath = tmp.resolve("test.aof");
        int port = freePort();

        // --- First run: write a command through a real socket ---
        Store store1 = new Store();
        AofWriter aof1 = new AofWriter(aofPath.toString());
        Dispatcher d1 = buildDispatcher(store1, aof1);
        Thread t1 = new Thread(() -> {
            try {
                new Server(port, d1, Map.of()).start();
            } catch (IOException ignored) {
            }
        });
        t1.setDaemon(true);
        t1.start();
        waitForPort(port, 2000);

        try (Socket s = new Socket("localhost", port)) {
            s.getOutputStream().write(bytes("*3\r\n$3\r\nset\r\n$1\r\nk\r\n$1\r\nv\r\n"));
            s.getOutputStream().flush();
            s.getInputStream().read(); // read +OK
        }
        aof1.close();

        long sizeAfterFirstRun = Files.size(aofPath);
        assertTrue(sizeAfterFirstRun > 0, "AOF should have content after write");

        // --- Second run: replay from AOF, verify data + AOF didn't grow ---
        int port2 = freePort();
        Store store2 = new Store();
        AofWriter aof2 = new AofWriter(aofPath.toString());
        Dispatcher d2 = buildDispatcher(store2, aof2);
        AofLoader.replay(aofPath.toString(), d2);

        // Data survived
        assertArrayEquals(bytes("v"),
                ((dev.anas.redis.store.Value.StringValue) store2.get(bytes("k"))).bytes());

        // AOF did NOT grow during replay
        long sizeAfterReplay = Files.size(aofPath);
        assertEquals(sizeAfterFirstRun, sizeAfterReplay,
                "AOF should not grow during replay — dispatchWithoutAof must be used");

        aof2.close();
    }
}