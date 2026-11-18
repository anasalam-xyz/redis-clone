package dev.anas.redis.command.handlers;

import dev.anas.redis.command.Command;
import dev.anas.redis.protocol.Reply;
import dev.anas.redis.store.Store;
import dev.anas.redis.store.StoreResult;

public final class HgetCommand implements Command {
    private final Store store;

    public HgetCommand(Store store) {
        this.store = store;
    }

    @Override
    public Reply execute(byte[][] args) {
        if (args.length != 2) {
            return new Reply.Error("ERR wrong number of arguments for 'hget' command");
        }

        StoreResult<byte[]> result = store.hashGet(args[0], args[1]);

        return switch (result) {
            case StoreResult.Success<byte[]> s -> s.value() == null ? new Reply.Null() : new Reply.BulkString(s.value());
            case StoreResult.WrongType<byte[]> w -> WRONGTYPE;
        };
    }

    @Override
    public boolean isWrite() {
        return false;
    }
}
