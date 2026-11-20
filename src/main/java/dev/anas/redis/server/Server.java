package dev.anas.redis.server;

import dev.anas.redis.command.Dispatcher;
import dev.anas.redis.pubsub.Broker;
import dev.anas.redis.pubsub.Command;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;

public final class Server {
    private final int port;
    private final Dispatcher dispatcher;
    private final Map<String, Command> pubsub;

    public Server(int port, Dispatcher dispatcher, Map<String, Command> pubsub) {
        this.port = port;
        this.dispatcher = dispatcher;
        this.pubsub = pubsub;
    }

    public void start() throws IOException {
        try (ServerSocket server = new ServerSocket(port)) {
            System.out.println("listening on " + port);
            while (true) {
                Socket client = server.accept();
                Thread.ofVirtual().start(new ClientHandler(client, dispatcher, pubsub));
            }
        }
    }
}