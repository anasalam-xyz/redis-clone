package dev.anas.redis.pubsub.handlers;

import dev.anas.redis.pubsub.Broker;
import dev.anas.redis.pubsub.Command;
import dev.anas.redis.protocol.Reply;
import dev.anas.redis.server.ClientHandler;
import dev.anas.redis.store.Key;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

public final class SubscribeCommand implements Command {
    private final Broker broker;

    public SubscribeCommand(Broker broker) {
        this.broker = broker;
    }

    @Override
    public void execute(byte[][] args, ClientHandler client) throws IOException {
        if (args.length < 2) {
            client.writeReply(new Reply.Error("ERR wrong number of arguments for 'subscribe' command"));
            return;
        }

        byte[] command = "subscribe".getBytes(StandardCharsets.US_ASCII);
        for (int i = 1; i < args.length; i++) {
            int count = broker.subscribe(client, args[i]);
            client.addSubscription(new Key(args[i]));
            client.writeReply(new Reply.Array(List.of(
                    new Reply.BulkString(command),
                    new Reply.BulkString(args[i]),
                    new Reply.Integer(count))));
        }
    }
}
