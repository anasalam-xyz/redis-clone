package dev.anas.redis.command.handlers;

import dev.anas.redis.command.Command;
import dev.anas.redis.protocol.Reply;
import dev.anas.redis.store.Store;
import java.nio.charset.StandardCharsets;

public final class PexpireCommand implements Command {
    private final Store store;

    public PexpireCommand(Store store) {
        this.store = store;
    }

    @Override
    public Reply execute(byte[][] args) {
        if (args.length != 2) {
            return new Reply.Error("ERR wrong number of arguments for 'pexpire' command");
        }

        long millis;
        try {
            millis = Long.parseLong(new String(args[1], StandardCharsets.US_ASCII));
        } catch (NumberFormatException e) {
            return new Reply.Error("ERR value is not an integer or out of range");
        }

        if (millis <= 0) {
            // Redis: EXPIRE k 0 or negative deletes the key immediately.
            return new Reply.Integer(store.del(args[0]) ? 1 : 0);
        }

        long expireAt = System.currentTimeMillis() + millis;
        return new Reply.Integer(store.setExpiry(args[0], expireAt) ? 1 : 0);
    }

    @Override
    public boolean isWrite() {
        return true;
    }

    @Override
    public byte[][] toAofArgs(byte[][] fullArgs) {
        long seconds = Long.parseLong(new String(fullArgs[2], StandardCharsets.US_ASCII));
        if (seconds <= 0)
            return fullArgs; // means "delete now", safe to replay as is
        long at = System.currentTimeMillis() + seconds;
        return new byte[][] {
                "PEXPIREAT".getBytes(StandardCharsets.US_ASCII),
                fullArgs[1],
                Long.toString(at).getBytes(StandardCharsets.US_ASCII)
        };
    }
}
