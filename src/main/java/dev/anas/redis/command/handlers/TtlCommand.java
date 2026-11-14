package dev.anas.redis.command.handlers;

import dev.anas.redis.command.Command;
import dev.anas.redis.protocol.Reply;
import dev.anas.redis.store.Store;

public final class TtlCommand implements Command {
    private final Store store;

    public TtlCommand(Store store) {
        this.store = store;
    }

    @Override
    public Reply execute(byte[][] args) {
        if (args.length != 1) {
            return new Reply.Error("ERR wrong number of arguments for 'ttl' command");
        }

        long ttl = store.getExpiry(args[0]);
        if (ttl == -1L) {
            return new Reply.Integer(-2L);
        }
        if (ttl == 0) {
            return new Reply.Integer(-1L);
        }

        long remainingMs = Math.max(0L, ttl - System.currentTimeMillis());
        return new Reply.Integer(remainingMs / 1000L);
    }

    @Override
    public boolean isWrite() {
        return false;
    }
}
