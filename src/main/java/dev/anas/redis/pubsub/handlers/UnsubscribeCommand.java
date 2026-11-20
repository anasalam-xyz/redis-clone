package dev.anas.redis.pubsub.handlers;

import dev.anas.redis.pubsub.Broker;
import dev.anas.redis.pubsub.Command;
import dev.anas.redis.protocol.Reply;
import dev.anas.redis.server.ClientHandler;
import dev.anas.redis.store.Key;

import java.util.Set;
import java.io.IOException;
import java.util.List;
import java.nio.charset.StandardCharsets;

public final class UnsubscribeCommand implements Command {
    private final Broker broker;

    public UnsubscribeCommand(Broker broker) {
        this.broker = broker;
    }

    @Override
    public void execute(byte[][] args, ClientHandler client) throws IOException {
        byte[] command = "unsubscribe".getBytes(StandardCharsets.US_ASCII);

        if (args.length == 1) {
            Set<Key> removed = broker.unsubscribeAll(client);
            client.clearSubscriptions();
            if (removed.isEmpty()) {
                client.writeReply(new Reply.Array(List.of(
                        new Reply.BulkString(command),
                        new Reply.Null(),
                        new Reply.Integer(0L))));
            } else {
                int remaining = removed.size();
                for (Key ch : removed) {
                    client.writeReply(new Reply.Array(List.of(
                            new Reply.BulkString(command),
                            new Reply.BulkString(ch.bytes()),
                            new Reply.Integer(--remaining))));
                }
            }
        } else if (args.length >= 2) {
            int remaining = 0;
            for (int i = 1; i < args.length; i++) {
                int count = broker.unsubscribe(client, args[i]);
                client.removeSubscription(new Key(args[i]));
                client.writeReply(new Reply.Array(List.of(
                        new Reply.BulkString(command),
                        new Reply.BulkString(args[i]),
                        new Reply.Integer(count))));
                remaining = count;
            }
            if (remaining == 0) {
                client.clearSubscriptions();
            }
        } else {
            client.writeReply(new Reply.Error("ERR wrong number of arguments for 'unsubscribe' command"));
        }
    }
}
