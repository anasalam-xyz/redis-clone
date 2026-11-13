package dev.anas.redis.command.handlers;

import dev.anas.redis.command.Command;
import dev.anas.redis.protocol.Reply;
import dev.anas.redis.store.Store;

public final class ExistsCommand implements Command {
    private final Store store;

    public ExistsCommand(Store store) {
        this.store = store;
    }

    @Override 
    public Reply execute(byte[][] args) {
        if(args.length != 1) {
            return new Reply.Error("ERR wrong number of arguments for 'exists' command");
        }

        return new Reply.Integer(store.exists(args[0]) ? 1 : 0);
    }

    @Override 
    public boolean isWrite() {
        return false;
    }
}
