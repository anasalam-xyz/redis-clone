package dev.anas.redis.command.handlers;

import dev.anas.redis.command.Command;
import dev.anas.redis.protocol.Reply;
import dev.anas.redis.store.Store;
import dev.anas.redis.store.Value;

import java.nio.charset.StandardCharsets;

public final class IncrByCommand implements Command {
    private final Store store;

    public IncrByCommand(Store store) {
        this.store = store;
    }

    @Override
    public Reply execute(byte[][] args) {
        if (args.length != 2) {
            return new Reply.Error("ERR wrong number of arguments for 'incrby' command");
        }

        long increment;
        try {
            increment = Long.parseLong(new String(args[1], StandardCharsets.US_ASCII));
        } catch (NumberFormatException e) {
            return new Reply.Error("ERR value is not an integer or out of range");
        }

        Value value = store.get(args[0]);
        if (value == null) {
            store.setKeepTtl(args[0], new Value.StringValue(Long.toString(increment).getBytes(StandardCharsets.US_ASCII)));
            return new Reply.Integer(increment);
        }
        if (!(value instanceof Value.StringValue sv)) {
            return WRONGTYPE;
        }

        long current;
        try {
            current = Long.parseLong(new String(sv.bytes(), StandardCharsets.US_ASCII));
        } catch (NumberFormatException e) {
            return new Reply.Error("ERR value is not an integer or out of range");
        }
        long newValue = current + increment;
        store.setKeepTtl(args[0], new Value.StringValue(Long.toString(newValue).getBytes(StandardCharsets.US_ASCII)));

        return new Reply.Integer(newValue);
    }

    @Override
    public boolean isWrite() {
        return true;
    }
}
