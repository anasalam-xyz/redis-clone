package dev.anas.redis.command.handlers;

import dev.anas.redis.command.Command;
import dev.anas.redis.protocol.Reply;
import dev.anas.redis.store.Store;
import dev.anas.redis.store.StoreResult;

public final class LpopCommand implements Command {
    private final Store store;

    public LpopCommand(Store store) {
        this.store = store;
    }

    @Override
    public Reply execute(byte[][] args) {
        if (args.length != 1) {
            return new Reply.Error("ERR wrong number of arguments for 'lpop' command");
        }

        StoreResult<byte[]> result = store.listPop(args[0], true);

        return switch (result) {
            case StoreResult.Success<byte[]> r -> r.value() == null ? new Reply.Null() : new Reply.BulkString(r.value());
            case StoreResult.WrongType<byte[]> w -> WRONGTYPE;
        };
    }

    @Override
    public boolean isWrite() {
        return true;
    }
}
