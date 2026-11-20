package dev.anas.redis;

import dev.anas.redis.command.Dispatcher;
import dev.anas.redis.command.handlers.SetCommand;
import dev.anas.redis.command.handlers.GetCommand;
import dev.anas.redis.pubsub.Broker;
import dev.anas.redis.pubsub.Command;
import dev.anas.redis.pubsub.handlers.PublishCommand;
import dev.anas.redis.pubsub.handlers.SubscribeCommand;
import dev.anas.redis.pubsub.handlers.UnsubscribeCommand;
import dev.anas.redis.server.Server;
import dev.anas.redis.store.Store;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PubSubIntegrationTest {

    private int port;

    private static byte[] bytes(String s) { return s.getBytes(StandardCharsets.US_ASCII); }

    @BeforeEach
    void startServer() throws Exception {
        try (ServerSocket probe = new ServerSocket(0)) { port = probe.getLocalPort(); }

        Store store = new Store();
        Dispatcher dispatcher = new Dispatcher();
        dispatcher.register("set", new SetCommand(store));
        dispatcher.register("get", new GetCommand(store));

        Broker broker = new Broker();
        Map<String, Command> pubsub = Map.of(
            "subscribe",   new SubscribeCommand(broker),
            "unsubscribe", new UnsubscribeCommand(broker),
            "publish",     new PublishCommand(broker)
        );

        Server server = new Server(port, dispatcher, pubsub);
        Thread t = new Thread(() -> {
            try { server.start(); } catch (IOException ignored) {}
        });
        t.setDaemon(true);
        t.start();
        waitForPort(port, 2000);
    }

    @Test
    void subscribeAndReceivePublishedMessage() throws Exception {
        try (Socket subscriber = new Socket("localhost", port);
             Socket publisher  = new Socket("localhost", port)) {

            // Subscriber subscribes
            subscriber.getOutputStream().write(bytes("*2\r\n$9\r\nsubscribe\r\n$4\r\nnews\r\n"));
            subscriber.getOutputStream().flush();
            assertArrayEquals(bytes("*3\r\n$9\r\nsubscribe\r\n$4\r\nnews\r\n:1\r\n"),
                              readReply(subscriber.getInputStream()));

            // Publisher publishes
            publisher.getOutputStream().write(bytes("*3\r\n$7\r\npublish\r\n$4\r\nnews\r\n$5\r\nhello\r\n"));
            publisher.getOutputStream().flush();
            assertArrayEquals(bytes(":1\r\n"), readReply(publisher.getInputStream()));

            // Subscriber receives message
            assertArrayEquals(bytes("*3\r\n$7\r\nmessage\r\n$4\r\nnews\r\n$5\r\nhello\r\n"),
                              readReply(subscriber.getInputStream()));
        }
    }

    @Test
    void publishWithNoSubscribersReturnsZero() throws Exception {
        try (Socket s = new Socket("localhost", port)) {
            s.getOutputStream().write(bytes("*3\r\n$7\r\npublish\r\n$4\r\nnope\r\n$1\r\nx\r\n"));
            s.getOutputStream().flush();
            assertArrayEquals(bytes(":0\r\n"), readReply(s.getInputStream()));
        }
    }

    @Test
    void unsubscribeStopsDelivery() throws Exception {
        try (Socket subscriber = new Socket("localhost", port);
             Socket publisher  = new Socket("localhost", port)) {

            subscriber.getOutputStream().write(bytes("*2\r\n$9\r\nsubscribe\r\n$4\r\nnews\r\n"));
            subscriber.getOutputStream().flush();
            readReply(subscriber.getInputStream());   // consume subscribe reply

            subscriber.getOutputStream().write(bytes("*2\r\n$11\r\nunsubscribe\r\n$4\r\nnews\r\n"));
            subscriber.getOutputStream().flush();
            assertArrayEquals(bytes("*3\r\n$11\r\nunsubscribe\r\n$4\r\nnews\r\n:0\r\n"),
                              readReply(subscriber.getInputStream()));

            publisher.getOutputStream().write(bytes("*3\r\n$7\r\npublish\r\n$4\r\nnews\r\n$1\r\nx\r\n"));
            publisher.getOutputStream().flush();
            assertArrayEquals(bytes(":0\r\n"), readReply(publisher.getInputStream()));
        }
    }

    @Test
    void subscriberModeRejectsOtherCommands() throws Exception {
        try (Socket s = new Socket("localhost", port)) {
            s.getOutputStream().write(bytes("*2\r\n$9\r\nsubscribe\r\n$4\r\nnews\r\n"));
            s.getOutputStream().flush();
            readReply(s.getInputStream());

            s.getOutputStream().write(bytes("*2\r\n$3\r\nget\r\n$1\r\nk\r\n"));
            s.getOutputStream().flush();
            byte[] reply = readReply(s.getInputStream());
            String str = new String(reply, StandardCharsets.US_ASCII);
            assertTrue(str.startsWith("-ERR"), "expected error, got: " + str);
            assertTrue(str.contains("only (P)SUBSCRIBE"));
        }
    }

    // ---- helpers (copy from ServerIntegrationTest) ----

    private static byte[] readReply(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        int first = in.read();
        if (first == -1) throw new IOException("EOF");
        buf.write(first);

        if (first == '+' || first == '-' || first == ':') {
            int prev = 0, cur;
            while ((cur = in.read()) != -1) {
                buf.write(cur);
                if (prev == '\r' && cur == '\n') return buf.toByteArray();
                prev = cur;
            }
            throw new IOException("EOF before CRLF");
        }

        if (first == '$') {
            int len = readHeaderLen(in, buf);
            if (len < 0) return buf.toByteArray();
            for (int i = 0; i < len + 2; i++) buf.write(in.read());
            return buf.toByteArray();
        }

        if (first == '*') {
            int count = readHeaderLen(in, buf);
            for (int i = 0; i < count; i++) {
                byte[] item = readReply(in);
                buf.write(item, 0, item.length);
            }
            return buf.toByteArray();
        }

        throw new IOException("unexpected first byte: " + first);
    }

    private static int readHeaderLen(InputStream in, ByteArrayOutputStream buf) throws IOException {
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

    private static void waitForPort(int port, long ms) {
        long deadline = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < deadline) {
            try (Socket probe = new Socket("localhost", port)) { return; }
            catch (IOException e) {
                try { Thread.sleep(20); }
                catch (InterruptedException ie) { Thread.currentThread().interrupt(); return; }
            }
        }
        throw new IllegalStateException("server didn't start on port " + port);
    }
}