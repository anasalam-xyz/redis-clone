package dev.anas.redis;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

public class Main {
    public static void main(String[] args) throws Exception {
        try (ServerSocket server = new ServerSocket(6380)) {
            System.out.println("Listening on 6380");
            while (true) {
                Socket client = server.accept();
                Thread.startVirtualThread(() -> handle(client));
            }
        }
    }

    static void handle(Socket client) {
        try (client) {
            InputStream in = client.getInputStream();
            OutputStream out = client.getOutputStream();
            byte[] buf = new byte[1024];
            int n;
            while ((n = in.read(buf)) != -1) {
                String raw = new String(buf, 0, n)
                        .replace("\r", "\\r").replace("\n", "\\n");
                System.out.println("Got " + n + " bytes: " + raw);
                out.write("+OK\r\n".getBytes());
                out.flush();
            }
        } catch (Exception e) {
            System.out.println("Client error: " + e.getMessage());
        }
    }
}