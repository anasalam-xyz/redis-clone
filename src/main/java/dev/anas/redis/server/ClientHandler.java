package dev.anas.redis.server;

import dev.anas.redis.protocol.*;
import dev.anas.redis.command.Dispatcher;
import dev.anas.redis.store.Key;
import dev.anas.redis.pubsub.Command;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;

public final class ClientHandler implements Runnable {
    private final Socket socket;
    private final Dispatcher dispatcher;
    private final Map<String, Command> pubsubCommands;
    private final RespParser parser = new RespParser();
    private final Set<Key> subscribedChannels = ConcurrentHashMap.newKeySet();
    private boolean subscribedMode = false;
    private OutputStream out;

    public ClientHandler(Socket socket, Dispatcher dispatcher, Map<String, Command> pubsubCommands) {
        this.socket = socket;
        this.dispatcher = dispatcher;
        this.pubsubCommands = pubsubCommands;
        if (socket != null) {
            try {
                socket.setTcpNoDelay(true);
            } catch (java.net.SocketException ignored) {
            }
        }
    }

    @Override
    public void run() {
        try (socket;
                InputStream in = socket.getInputStream();
                OutputStream out = new BufferedOutputStream(socket.getOutputStream())) {
            this.out = out;
            byte[] chunk = new byte[8192];
            int n;
            while ((n = in.read(chunk)) != -1) {
                parser.append(chunk, 0, n);
                boolean needFlush = false;

                while (true) {
                    ParseResult r = parser.next();
                    if (r instanceof ParseResult.NeedMore) {
                        break;
                    }
                    if (r instanceof ParseResult.ParseError e) {
                        writeReply(new Reply.Error("ERR " + e.message()));
                        return;
                    }
                    ParseResult.Parsed p = (ParseResult.Parsed) r;

                    String name = new String(p.args()[0], StandardCharsets.US_ASCII).toLowerCase(Locale.ROOT);
                    Command pubsubCmd = pubsubCommands.get(name);
                    if (pubsubCmd != null) {
                        pubsubCmd.execute(p.args(), this);
                    } else if (subscribedMode && !name.equals("ping") && !name.equals("quit")) {
                        writeReply(new Reply.Error("ERR Can't execute '" + name
                                + "': only (P)SUBSCRIBE / (P)UNSUBSCRIBE / PING / QUIT allowed in this context"));
                    } else {
                        Reply reply = dispatcher.dispatch(p.args());
                        synchronized (this) {
                            RespWriter.writeTo(out, reply);
                        }
                        needFlush = true;
                    }
                }

                if (needFlush) {
                    synchronized (this) {
                        out.flush();
                    }
                }
            }
        } catch (IOException ignored) {
        }
    }

    public synchronized void sendPubSubMessage(byte[] channel, byte[] message) {
        try {
            RespWriter.writeTo(out, new Reply.Array(List.of(
                    new Reply.BulkString("message".getBytes(StandardCharsets.US_ASCII)),
                    new Reply.BulkString(channel),
                    new Reply.BulkString(message))));
            out.flush();
        } catch (IOException ignored) {
            // subscriber disconnected
        }
    }

    public synchronized void writeReply(Reply reply) throws IOException {
        RespWriter.writeTo(out, reply);
        out.flush();
    }

    public void addSubscription(Key channel) {
        subscribedChannels.add(channel);
        subscribedMode = true;
    }

    public void removeSubscription(Key channel) {
        subscribedChannels.remove(channel);
        if (subscribedChannels.isEmpty()) {
            subscribedMode = false;
        }
    }

    public void clearSubscriptions() {
        subscribedChannels.clear();
        subscribedMode = false;
    }

    public boolean isSubscribedMode() {
        return subscribedMode;
    }
}