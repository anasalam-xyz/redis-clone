package dev.anas.redis.command.handlers;

import dev.anas.redis.command.Command;
import dev.anas.redis.protocol.Reply;
import dev.anas.redis.store.Store;

public final class PersistCommand implements Command {
    private final Store store;

    public PersistCommand(Store store) {
        this.store = store;
    }

    @Override
    public Reply execute(byte[][] args) {
        if (args.length != 1) {
            return new Reply.Error("ERR wrong number of arguments for 'persist' command");
        }

        return new Reply.Integer(store.persist(args[0]) ? 1L : 0L);
    }

    @Override
    public boolean isWrite() {
        return true;
    }
}
