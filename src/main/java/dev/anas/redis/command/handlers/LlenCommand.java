package dev.anas.redis.command.handlers;

import dev.anas.redis.command.Command;
import dev.anas.redis.protocol.Reply;
import dev.anas.redis.store.Store;
import dev.anas.redis.store.StoreResult;

public final class LlenCommand implements Command {
    private final Store store;

    public LlenCommand(Store store) {
        this.store = store;
    }

    @Override
    public Reply execute(byte[][] args) {
        if (args.length != 1) {
            return new Reply.Error("ERR wrong number of arguments for 'llen' command");
        }

        StoreResult<Long> result = store.listLen(args[0]);

        return switch (result) {
            case StoreResult.Success<Long> r -> new Reply.Integer(r.value());
            case StoreResult.WrongType<Long> w -> WRONGTYPE;
        };
    }

    @Override
    public boolean isWrite() {
        return false;
    }
}
