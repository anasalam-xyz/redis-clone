package dev.anas.redis.command.handlers;

import dev.anas.redis.command.Command;
import dev.anas.redis.command.Dispatcher;
import dev.anas.redis.protocol.Reply;
import dev.anas.redis.store.Store;
import java.io.IOException;

public final class BgrewriteaofCommand implements Command {
    private final Dispatcher dispatcher;
    private final Store store;

    public BgrewriteaofCommand(Dispatcher dispatcher, Store store) {
        this.dispatcher = dispatcher;
        this.store = store;
    }

    @Override
    public Reply execute(byte[][] args) {
        if (args.length != 0) {
            return new Reply.Error("ERR wrong number of arguments for 'rewriteaof' command");
        }
        if (!dispatcher.aofEnabled()) {
            return new Reply.Error("ERR AOF is not enabled (start with --appendonly yes)");
        }
        try {
            if (!dispatcher.rewriteAof(store::snapshotAsCommands)) {
                return new Reply.Error("ERR AOF rewrite already in progress");
            }
            return new Reply.SimpleString("OK");
        } catch (IOException e) {
            return new Reply.Error("ERR AOF rewrite failed: " + e.getMessage());
        }
    }

    @Override
    public boolean isWrite() {
        return false;
    }
}