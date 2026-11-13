package dev.anas.redis.command.handlers;

import dev.anas.redis.command.Command;
import dev.anas.redis.protocol.Reply;

public final class EchoCommand implements Command {
    
    @Override 
    public Reply execute(byte[][] args) {
        if(args.length != 1) {
            return new Reply.Error("ERR wrong number of arguments for 'echo' command");
        }

        return new Reply.BulkString(args[0]);
    }

    @Override 
    public boolean isWrite() {
        return false;
    }
}
