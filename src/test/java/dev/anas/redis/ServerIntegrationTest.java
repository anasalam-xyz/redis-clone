package dev.anas.redis;

import dev.anas.redis.command.Dispatcher;
import dev.anas.redis.command.handlers.DelCommand;
import dev.anas.redis.command.handlers.EchoCommand;
import dev.anas.redis.command.handlers.ExistsCommand;
import dev.anas.redis.command.handlers.GetCommand;
import dev.anas.redis.command.handlers.PingCommand;
import dev.anas.redis.command.handlers.SetCommand;
import dev.anas.redis.server.Server;
import dev.anas.redis.store.Store;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ServerIntegrationTest {

    private int port;

    @BeforeEach
    void startServer() throws Exception {
        // Find a free port by binding to 0, letting the OS pick, then releasing.
        try (ServerSocket probe = new ServerSocket(0)) {
            port = probe.getLocalPort();
        }

        Store store = new Store();
        Dispatcher dispatcher = new Dispatcher();
        dispatcher.register("ping",   new PingCommand());
        dispatcher.register("echo",   new EchoCommand());
        dispatcher.register("set",    new SetCommand(store));
        dispatcher.register("get",    new GetCommand(store));
        dispatcher.register("del",    new DelCommand(store));
        dispatcher.register("exists", new ExistsCommand(store));

        Server server = new Server(port, dispatcher, Map.of());
        Thread t = new Thread(() -> {
            try { server.start(); } catch (IOException ignored) {}
        });
        t.setDaemon(true);                 // dies with the JVM; don't hang `mvn test`
        t.start();

        waitForPort(port, 2000);
    }

    @Test
    void pingReturnsPong() throws IOException {
        try (Socket s = new Socket("localhost", port)) {
            s.getOutputStream().write("*1\r\n$4\r\nping\r\n".getBytes(StandardCharsets.US_ASCII));
            s.getOutputStream().flush();
            assertArrayEquals("+PONG\r\n".getBytes(StandardCharsets.US_ASCII), readReply(s.getInputStream()));
        }
    }

    @Test
    void setThenGetReturnsValue() throws IOException {
        try (Socket s = new Socket("localhost", port)) {
            OutputStream out = s.getOutputStream();
            InputStream in = s.getInputStream();

            out.write("*3\r\n$3\r\nset\r\n$1\r\nk\r\n$1\r\nv\r\n".getBytes(StandardCharsets.US_ASCII));
            out.flush();
            assertArrayEquals("+OK\r\n".getBytes(StandardCharsets.US_ASCII), readReply(in));

            out.write("*2\r\n$3\r\nget\r\n$1\r\nk\r\n".getBytes(StandardCharsets.US_ASCII));
            out.flush();
            assertArrayEquals("$1\r\nv\r\n".getBytes(StandardCharsets.US_ASCII), readReply(in));
        }
    }

    @Test
    void pipelinedCommandsBothExecuteInOrder() throws IOException {
        try (Socket s = new Socket("localhost", port)) {
            // SET k v and GET k in one write — tests ClientHandler's drain loop.
            String pipelined =
                "*3\r\n$3\r\nset\r\n$1\r\nk\r\n$1\r\nv\r\n" +
                "*2\r\n$3\r\nget\r\n$1\r\nk\r\n";
            s.getOutputStream().write(pipelined.getBytes(StandardCharsets.US_ASCII));
            s.getOutputStream().flush();

            assertArrayEquals("+OK\r\n".getBytes(StandardCharsets.US_ASCII),   readReply(s.getInputStream()));
            assertArrayEquals("$1\r\nv\r\n".getBytes(StandardCharsets.US_ASCII), readReply(s.getInputStream()));
        }
    }

    @Test
    void splitCommandAcrossTwoWrites() throws Exception {
        try (Socket s = new Socket("localhost", port)) {
            OutputStream out = s.getOutputStream();

            out.write("*1\r\n$4\r\npi".getBytes(StandardCharsets.US_ASCII));   // incomplete
            out.flush();
            Thread.sleep(50);

            out.write("ng\r\n".getBytes(StandardCharsets.US_ASCII));            // completes it
            out.flush();

            assertArrayEquals("+PONG\r\n".getBytes(StandardCharsets.US_ASCII), readReply(s.getInputStream()));
        }
    }

    @Test
    void unknownCommandReturnsErrorAndKeepsConnectionOpen() throws IOException {
        try (Socket s = new Socket("localhost", port)) {
            OutputStream out = s.getOutputStream();
            InputStream in = s.getInputStream();

            out.write("*1\r\n$5\r\nnope!\r\n".getBytes(StandardCharsets.US_ASCII));
            out.flush();
            byte[] err = readReply(in);
            String errStr = new String(err, StandardCharsets.US_ASCII);
            assertTrue(errStr.startsWith("-ERR"), "expected error, got: " + errStr);
            assertTrue(errStr.contains("unknown command"));

            // The connection should still work after an unknown command.
            out.write("*1\r\n$4\r\nping\r\n".getBytes(StandardCharsets.US_ASCII));
            out.flush();
            assertArrayEquals("+PONG\r\n".getBytes(StandardCharsets.US_ASCII), readReply(in));
        }
    }

    /** Reads one complete RESP reply from `in` and returns its raw bytes. */
    private static byte[] readReply(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        int first = in.read();
        if (first == -1) throw new IOException("EOF before any reply");
        buf.write(first);

        // +simple, -error, :integer — read until \r\n
        if (first == '+' || first == '-' || first == ':') {
            readUntilCrlf(in, buf);
            return buf.toByteArray();
        }

        // $bulk — read header "$N\r\n", then N bytes + \r\n (or handle $-1)
        if (first == '$') {
            int len = readBulkHeaderLen(in, buf);
            if (len < 0) return buf.toByteArray();   // $-1\r\n
            for (int i = 0; i < len + 2; i++) buf.write(in.read());
            return buf.toByteArray();
        }

        throw new IOException("unexpected first byte: " + first);
    }

    private static void readUntilCrlf(InputStream in, ByteArrayOutputStream buf) throws IOException {
        int prev = 0, cur;
        while ((cur = in.read()) != -1) {
            buf.write(cur);
            if (prev == '\r' && cur == '\n') return;
            prev = cur;
        }
        throw new IOException("EOF before CRLF");
    }

    private static int readBulkHeaderLen(InputStream in, ByteArrayOutputStream buf) throws IOException {
        // After the '$' we've already written, read digits until \r\n.
        StringBuilder digits = new StringBuilder();
        int prev = 0, cur;
        while ((cur = in.read()) != -1) {
            buf.write(cur);
            if (prev == '\r' && cur == '\n') break;
            if (cur != '\r' && cur != '\n') digits.append((char) cur);
            prev = cur;
        }
        return Integer.parseInt(digits.toString());
    }

    private static void waitForPort(int port, long millis) {
        long deadline = System.currentTimeMillis() + millis;
        while (System.currentTimeMillis() < deadline) {
            try (Socket probe = new Socket("localhost", port)) { return; }
            catch (IOException e) {
                try { Thread.sleep(20); }
                catch (InterruptedException ie) { Thread.currentThread().interrupt(); return; }
            }
        }
        throw new IllegalStateException("server didn't bind port " + port + " within " + millis + "ms");
    }
}