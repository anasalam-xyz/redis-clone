package dev.anas.redis.command.handlers;

import dev.anas.redis.command.Command;
import dev.anas.redis.protocol.Reply;
import dev.anas.redis.store.Store;
import dev.anas.redis.store.StoreResult;

import java.util.Arrays;

public final class HdelCommand implements Command {
    private final Store store;

    public HdelCommand(Store store) {
        this.store = store;
    }

    @Override
    public Reply execute(byte[][] args) {
        if (args.length < 2) {
            return new Reply.Error("ERR wrong number of arguments for 'hdel' command");
        }

        StoreResult<Long> result = store.hashDel(args[0], Arrays.copyOfRange(args, 1, args.length));

        return switch (result) {
            case StoreResult.Success<Long> s -> new Reply.Integer(s.value());
            case StoreResult.WrongType<Long> w -> WRONGTYPE;
        };
    }

    @Override
    public boolean isWrite() {
        return true;
    }
}
