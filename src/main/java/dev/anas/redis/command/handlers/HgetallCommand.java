package dev.anas.redis.command.handlers;

import dev.anas.redis.command.Command;
import dev.anas.redis.protocol.Reply;
import dev.anas.redis.store.Store;
import dev.anas.redis.store.StoreResult;
import dev.anas.redis.store.Key;

import java.util.Map;
import java.util.List;
import java.util.ArrayList;

public final class HgetallCommand implements Command {
    private final Store store;

    public HgetallCommand(Store store) {
        this.store = store;
    }

    @Override
    public Reply execute(byte[][] args) {
        if (args.length != 1) {
            return new Reply.Error("ERR wrong number of arguments for 'hgetall' command");
        }

        StoreResult<Map<Key, byte[]>> result = store.hashGetAll(args[0]);

        return switch (result) {
            case StoreResult.Success<Map<Key, byte[]>> s -> {
                List<Reply> list = new ArrayList<>();
                for(Map.Entry<Key, byte[]> entry: s.value().entrySet()) {
                    list.add(new Reply.BulkString(entry.getKey().bytes()));
                    list.add(new Reply.BulkString(entry.getValue()));
                }
                yield new Reply.Array(list);
            }
            case StoreResult.WrongType<Map<Key, byte[]>> w -> WRONGTYPE;
        };
    }

    @Override
    public boolean isWrite() {
        return false;
    }
}
