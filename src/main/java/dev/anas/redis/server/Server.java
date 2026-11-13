package dev.anas.redis.server;

import dev.anas.redis.command.Dispatcher;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public final class Server {
    private final int port;
    private final Dispatcher dispatcher;

    public Server(int port, Dispatcher dispatcher) {
        this.port = port;
        this.dispatcher = dispatcher;
    }

    public void start() throws IOException {
        try (ServerSocket server = new ServerSocket(port)) {
            System.out.println("listening on " + port);
            while (true) {
                Socket client = server.accept();
                Thread.ofVirtual().start(new ClientHandler(client, dispatcher));
            }
        }
    }
}