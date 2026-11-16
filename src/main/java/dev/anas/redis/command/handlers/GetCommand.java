package dev.anas.redis.command.handlers;

import dev.anas.redis.command.Command;
import dev.anas.redis.protocol.Reply;
import dev.anas.redis.store.Store;
import dev.anas.redis.store.Value;

public final class GetCommand implements Command {
    private final Store store;

    public GetCommand(Store store) {
        this.store = store;
    }

    @Override 
    public Reply execute(byte[][] args) {
        if(args.length != 1) {
            return new Reply.Error("ERR wrong number of arguments for 'get' command");
        }

        Value value = store.get(args[0]);
        if(value == null) {
            return new Reply.Null();
        }
        if(!(value instanceof Value.StringValue sv)) {
            return WRONGTYPE;
        }

        return new Reply.BulkString(sv.bytes());
    }

    @Override 
    public boolean isWrite() {
        return false;
    }
}
