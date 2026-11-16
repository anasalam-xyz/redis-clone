package dev.anas.redis.command.handlers;

import dev.anas.redis.command.Command;
import dev.anas.redis.protocol.Reply;
import dev.anas.redis.store.Store;
import dev.anas.redis.store.Value;

public final class AppendCommand implements Command {
    private final Store store;

    public AppendCommand(Store store) {
        this.store = store;
    }

    @Override
    public Reply execute(byte[][] args) {
        if (args.length != 2) {
            return new Reply.Error("ERR wrong number of arguments for 'append' command");
        }

        Value value = store.get(args[0]);
        if (value == null) {
            store.setKeepTtl(args[0], new Value.StringValue(args[1]));
            return new Reply.Integer(args[1].length);
        }
        if (!(value instanceof Value.StringValue sv)) {
            return WRONGTYPE;
        }

        byte[] oldValue = sv.bytes();
        int oldLength = oldValue.length;
        int newLength = oldLength + args[1].length;
        byte[] newValue = new byte[newLength];

        System.arraycopy(oldValue, 0, newValue, 0, oldLength);
        System.arraycopy(args[1], 0, newValue, oldLength, args[1].length);

        store.setKeepTtl(args[0], new Value.StringValue(newValue));

        return new Reply.Integer(newLength);
    }

    @Override
    public boolean isWrite() {
        return true;
    }
}
