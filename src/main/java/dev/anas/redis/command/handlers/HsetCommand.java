package dev.anas.redis.command.handlers;

import dev.anas.redis.command.Command;
import dev.anas.redis.protocol.Reply;
import dev.anas.redis.store.Store;
import dev.anas.redis.store.StoreResult;

import java.util.Arrays;

public final class HsetCommand implements Command {
    private final Store store;

    public HsetCommand(Store store) {
        this.store = store;
    }

    @Override
    public Reply execute(byte[][] args) {
        if (args.length < 3 || args.length % 2 == 0) {
            return new Reply.Error("ERR wrong number of arguments for 'hset' command");
        }

        StoreResult<Long> result = store.hashSet(args[0], Arrays.copyOfRange(args, 1, args.length));

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
