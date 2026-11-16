package dev.anas.redis.command.handlers;

import dev.anas.redis.command.Command;
import dev.anas.redis.protocol.Reply;
import dev.anas.redis.store.Store;
import dev.anas.redis.store.StoreResult;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class LrangeCommand implements Command {
    private final Store store;

    public LrangeCommand(Store store) {
        this.store = store;
    }

    @Override
    public Reply execute(byte[][] args) {
        if (args.length != 3) {
            return new Reply.Error("ERR wrong number of arguments for 'lrange' command");
        }

        long start;
        long stop;
        try {
            start = Long.parseLong(new String(args[1], StandardCharsets.US_ASCII));
            stop = Long.parseLong(new String(args[2], StandardCharsets.US_ASCII));
        } catch (NumberFormatException e) {
            return new Reply.Error("ERR value is not an integer or out of range");
        }
        StoreResult<List<byte[]>> result = store.listRange(args[0], start, stop);

        return switch (result) {
            case StoreResult.Success<List<byte[]>> r -> {
                List<Reply> list = new ArrayList<>();
                for(byte[] element: r.value()) {
                    list.add(new Reply.BulkString(element));
                }
                yield new Reply.Array(list);
            }
            case StoreResult.WrongType<List<byte[]>> w -> WRONGTYPE;
        };
    }

    @Override
    public boolean isWrite() {
        return false;
    }
}
