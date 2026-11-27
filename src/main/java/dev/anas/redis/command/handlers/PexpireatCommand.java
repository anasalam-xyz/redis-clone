package dev.anas.redis.command.handlers;

import dev.anas.redis.command.Command;
import dev.anas.redis.protocol.Reply;
import dev.anas.redis.store.Store;
import java.nio.charset.StandardCharsets;

public final class PexpireatCommand implements Command {
    private final Store store;

    public PexpireatCommand(Store store) {
        this.store = store;
    }

    @Override
    public Reply execute(byte[][] args) {
        if (args.length != 2) {
            return new Reply.Error("ERR wrong number of arguments for 'pexpireat' command");
        }

        long expireAt;
        try {
            expireAt = Long.parseLong(new String(args[1], StandardCharsets.US_ASCII));
        } catch (NumberFormatException e) {
            return new Reply.Error("ERR value is not an integer or out of range");
        }

        if (expireAt <= System.currentTimeMillis()) {
            // already in the past: the key is gone (this is what happens on replay of an old TTL)
            return new Reply.Integer(store.del(args[0]) ? 1 : 0);
        }
        return new Reply.Integer(store.setExpiry(args[0], expireAt) ? 1 : 0);
    }

    @Override
    public boolean isWrite() {
        return true;
    }
}