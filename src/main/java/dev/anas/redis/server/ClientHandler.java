package dev.anas.redis.server;

import dev.anas.redis.protocol.*;
import dev.anas.redis.command.Dispatcher;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Arrays;

public final class ClientHandler implements Runnable {
    private final Socket socket;
    private final Dispatcher dispatcher;
    private final RespParser parser = new RespParser();

    public ClientHandler(Socket socket, Dispatcher dispatcher) {
        this.socket = socket;
        this.dispatcher = dispatcher;
    }

    @Override
    public void run() {
        try (socket;
                InputStream in = new BufferedInputStream(socket.getInputStream());
                OutputStream out = new BufferedOutputStream(socket.getOutputStream())) {

            byte[] chunk = new byte[8192];
            int n;
            while ((n = in.read(chunk)) != -1) {
                ParseResult r = parser.feed(Arrays.copyOf(chunk, n));

                while (true) {
                    if (r instanceof ParseResult.Parsed p) {
                        Reply reply = dispatcher.dispatch(p.args());
                        out.write(RespWriter.write(reply));
                        out.flush();
                        r = parser.feed(new byte[0]);
                    } else if (r instanceof ParseResult.NeedMore) {
                        break; // back to read()
                    } else if (r instanceof ParseResult.ParseError e) {
                        out.write(RespWriter.write(new Reply.Error("ERR " + e.message())));
                        out.flush();
                        return; // exits run() -> socket closes
                    }
                }
            }
        } catch (IOException e) {
            // Client disconnected or socket error
            // The socket is already closed by try-with-resources; this thread ends.
            // Log here if debugging: System.err.println("client gone: " + e.getMessage());
        }
    }
}