package dev.anas.redis.command.handlers;

import dev.anas.redis.command.Command;
import dev.anas.redis.protocol.Reply;
import dev.anas.redis.store.Store;

public final class SetCommand implements Command {
    private final Store store;

    public SetCommand(Store store) {
        this.store = store;
    }

    @Override 
    public Reply execute(byte[][] args) {
        if(args.length != 2) {
            return new Reply.Error("ERR wrong number of arguments for 'set' command");
        }

        store.set(args[0], args[1], 0L);

        return new Reply.SimpleString("OK");
    }

    @Override 
    public boolean isWrite() {
        return true;
    }
}
