package dev.anas.redis.command;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Locale;

import dev.anas.redis.protocol.Reply;

public final class Dispatcher {
    private final Map<String, Command> commands = new HashMap<>();

    public void register(String name, Command command) {
        commands.put(name.toLowerCase(Locale.ROOT), command);
    }

    public Reply dispatch(byte[][] args) {
        if (args.length == 0) {
            return new Reply.Error("ERR empty command");
        }

        String name = new String(args[0], StandardCharsets.US_ASCII).toLowerCase(Locale.ROOT);
        Command command = commands.get(name);

        if (command == null) {
            return new Reply.Error("ERR unknown command '" + name + "'");
        }

        return command.execute(Arrays.copyOfRange(args, 1, args.length));
    }
}
