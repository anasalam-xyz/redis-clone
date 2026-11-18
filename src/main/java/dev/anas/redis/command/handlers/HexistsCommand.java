package dev.anas.redis.command.handlers;

import dev.anas.redis.command.Command;
import dev.anas.redis.protocol.Reply;
import dev.anas.redis.store.Store;
import dev.anas.redis.store.StoreResult;

public final class HexistsCommand implements Command {
    private final Store store;

    public HexistsCommand(Store store) {
        this.store = store;
    }

    @Override
    public Reply execute(byte[][] args) {
        if (args.length != 2) {
            return new Reply.Error("ERR wrong number of arguments for 'hexists' command");
        }

        StoreResult<Boolean> result = store.hashExists(args[0], args[1]);

        return switch (result) {
            case StoreResult.Success<Boolean> s -> new Reply.Integer(s.value() ? 1L : 0L);
            case StoreResult.WrongType<Boolean> w -> WRONGTYPE;
        };
    }

    @Override
    public boolean isWrite() {
        return false;
    }
}
