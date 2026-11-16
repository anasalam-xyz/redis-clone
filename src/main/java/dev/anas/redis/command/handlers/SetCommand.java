package dev.anas.redis.command.handlers;

import dev.anas.redis.command.Command;
import dev.anas.redis.protocol.Reply;
import dev.anas.redis.store.Store;
import dev.anas.redis.store.Value;

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

        Value value = store.get(args[0]);
        if(value != null && !(value instanceof Value.StringValue)) {
            return WRONGTYPE;
        }

        store.set(args[0], new Value.StringValue(args[1]), 0L);

        return new Reply.SimpleString("OK");
    }

    @Override 
    public boolean isWrite() {
        return true;
    }
}
