package dev.anas.redis.command;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import java.util.Locale;

import dev.anas.redis.persistence.AofWriter;
import dev.anas.redis.protocol.Reply;

public final class Dispatcher {
    private final ReentrantLock writeOrder = new ReentrantLock();   // not synchronized: JDK 21 pins carrier threads on file I/O inside it
    private final Map<String, Command> commands = new HashMap<>();
    private AofWriter aof;

    public Dispatcher() {
        this.aof = null;
    }

    public Dispatcher(AofWriter aof) {
        this.aof = aof;
    }

    public void register(String name, Command command) {
        commands.put(name.toLowerCase(Locale.ROOT), command);
    }

    public Reply dispatch(byte[][] args) {
        return dispatchInternal(args, true);
    }

    public Reply dispatchWithoutAof(byte[][] args) {
        return dispatchInternal(args, false);
    }

    private Reply dispatchInternal(byte[][] args, boolean persist) {
        if (args.length == 0) {
            return new Reply.Error("ERR empty command");
        }

        String name = new String(args[0], StandardCharsets.US_ASCII).toLowerCase(Locale.ROOT);
        Command command = commands.get(name);
        if (command == null) {
            return new Reply.Error("ERR unknown command '" + name + "'");
        }

        byte[][] rest = Arrays.copyOfRange(args, 1, args.length);
        if (!(persist && aof != null && command.isWrite())) {
            return command.execute(rest);
        }
        writeOrder.lock();
        try {
            Reply reply = command.execute(rest);
            if (!(reply instanceof Reply.Error)) {
                try {
                    aof.append(args);
                } catch (IOException e) {
                    System.err.println("AOF write failed: " + e.getMessage());
                }
            }
            return reply;
        } finally {
            writeOrder.unlock();
        }
    }
}
