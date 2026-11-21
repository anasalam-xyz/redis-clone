package dev.anas.redis.command.handlers;

import java.nio.charset.StandardCharsets;
import java.util.List;

import dev.anas.redis.Config;
import dev.anas.redis.command.Command;
import dev.anas.redis.protocol.Reply;

public final class ConfigCommand implements Command {
    private final Config config;

    public ConfigCommand(Config config) {
        this.config = config;
    }

    @Override
    public Reply execute(byte[][] args) {
        if (args.length != 2 || !text(args[0]).equalsIgnoreCase("get")) {
            return new Reply.Error("ERR only CONFIG GET <name> is supported");
        }
        String name = text(args[1]).toLowerCase();
        return switch (name) {
            case "save" -> pair("save", "");
            case "appendonly" -> pair("appendonly", config.appendOnly() ? "yes" : "no");
            case "maxmemory" -> pair("maxmemory", Long.toString(config.maxMemory()));
            default -> new Reply.Array(List.of());
        };
    }

    @Override
    public boolean isWrite() {
        return false; // read-only: must never be appended to the AOF
    }

    private static Reply pair(String name, String value) {
        return new Reply.Array(List.of(bulk(name), bulk(value)));
    }

    private static Reply bulk(String s) {
        return new Reply.BulkString(s.getBytes(StandardCharsets.UTF_8));
    }

    private static String text(byte[] b) {
        return new String(b, StandardCharsets.UTF_8);
    }
}