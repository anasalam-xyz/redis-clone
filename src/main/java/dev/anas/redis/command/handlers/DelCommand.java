package dev.anas.redis.command.handlers;

import dev.anas.redis.command.Command;
import dev.anas.redis.protocol.Reply;
import dev.anas.redis.store.Store;

public final class DelCommand implements Command {
    private final Store store;

    public DelCommand(Store store) {
        this.store = store;
    }

    @Override 
    public Reply execute(byte[][] args) {
        if(args.length != 1) {
            return new Reply.Error("ERR wrong number of arguments for 'del' command");
        }

        return new Reply.Integer(store.del(args[0]) ? 1 : 0);
    }

    @Override 
    public boolean isWrite() {
        return true;
    }
}
