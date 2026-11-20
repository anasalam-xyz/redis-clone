package dev.anas.redis.pubsub;

import dev.anas.redis.server.ClientHandler;

import java.io.IOException;
 
public interface Command {
    void execute(byte[][] args, ClientHandler client ) throws IOException;
}