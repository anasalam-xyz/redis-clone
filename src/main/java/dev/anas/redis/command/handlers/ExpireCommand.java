package dev.anas.redis.command.handlers;

import dev.anas.redis.command.Command;
import dev.anas.redis.protocol.Reply;
import dev.anas.redis.store.Store;
import java.nio.charset.StandardCharsets;

public final class ExpireCommand implements Command {
    private final Store store;

    public ExpireCommand(Store store) {
        this.store = store;
    }

    @Override
    public Reply execute(byte[][] args) {
        if (args.length != 2) {
            return new Reply.Error("ERR wrong number of arguments for 'expire' command");
        }

        long seconds;
        try {
            seconds = Long.parseLong(new String(args[1], StandardCharsets.US_ASCII));
        } catch (NumberFormatException e) {
            return new Reply.Error("ERR value is not an integer or out of range");
        }

        if (seconds <= 0) {
            // Redis: EXPIRE k 0 or negative deletes the key immediately.
            return new Reply.Integer(store.del(args[0]) ? 1 : 0);
        }

        long expireAt = System.currentTimeMillis() + seconds * 1000L;
        return new Reply.Integer(store.setExpiry(args[0], expireAt) ? 1 : 0);
    }

    @Override
    public boolean isWrite() {
        return true;
    }
}
