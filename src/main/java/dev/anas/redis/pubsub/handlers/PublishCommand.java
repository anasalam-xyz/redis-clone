package dev.anas.redis.pubsub.handlers;

import dev.anas.redis.pubsub.Broker;
import dev.anas.redis.pubsub.Command;
import dev.anas.redis.protocol.Reply;
import dev.anas.redis.server.ClientHandler;

import java.io.IOException;

public final class PublishCommand implements Command {
    private final Broker broker;

    public PublishCommand(Broker broker) {
        this.broker = broker;
    }

    @Override
    public void execute(byte[][] args, ClientHandler client) throws IOException {
        if (args.length != 3) {
            client.writeReply(new Reply.Error("ERR wrong number of arguments for 'publish' command"));
            return;
        } 

        int receivers = broker.publish(args[1], args[2]);
        client.writeReply(new Reply.Integer((long) receivers));
    }
}
