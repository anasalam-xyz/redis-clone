package dev.anas.redis.command.handlers;

import dev.anas.redis.command.Command;
import dev.anas.redis.protocol.Reply;

public final class PingCommand implements Command {

    @Override
    public Reply execute(byte[][] args) {
        if (args.length != 0) {
            return new Reply.Error("ERR wrong number of arguments for 'ping' command");
        }
        return new Reply.SimpleString("PONG");
    }

    @Override
    public boolean isWrite() {
        return false;
    }
}