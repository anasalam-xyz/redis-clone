package dev.anas.redis.command.handlers;

import java.util.Arrays;

import dev.anas.redis.command.Command;
import dev.anas.redis.protocol.Reply;
import dev.anas.redis.store.Store;
import dev.anas.redis.store.StoreResult;

public final class LpushCommand implements Command {
    private final Store store;

    public LpushCommand(Store store) {
        this.store = store;
    }

    @Override
    public Reply execute(byte[][] args) {
        if (args.length < 2) {
            return new Reply.Error("ERR wrong number of arguments for 'lpush' command");
        }

        StoreResult<Long> result = store.listPushAll(args[0], Arrays.copyOfRange(args, 1, args.length), true);

        return switch (result) {
            case StoreResult.Success<Long> r -> new Reply.Integer(r.value());
            case StoreResult.WrongType<Long> w -> WRONGTYPE;
        };
    }

    @Override
    public boolean isWrite() {
        return true;
    }
}
