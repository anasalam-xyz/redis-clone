package dev.anas.redis.command.handlers;

import dev.anas.redis.command.Command;
import dev.anas.redis.protocol.Reply;
import dev.anas.redis.store.Store;
import dev.anas.redis.store.Value;

public final class SetCommand implements Command {
    private final Store store;

    public SetCommand(Store store) {
        this.store = store;
    }

    @Override
    public Reply execute(byte[][] args) {
        if (args.length < 2) {
            return new Reply.Error("ERR wrong number of arguments for 'set' command");
        }

        // Parse options starting at index 2
        long expireAtMillis = 0L;
        boolean nx = false, xx = false;

        for (int i = 2; i < args.length; i++) {
            String opt = ascii(args[i]).toUpperCase(java.util.Locale.ROOT);

            switch (opt) {
                case "EX", "PX", "EXAT", "PXAT" -> {
                    if (i + 1 >= args.length) {
                        return new Reply.Error("ERR syntax error");
                    }
                    if (expireAtMillis != 0L) {
                        return new Reply.Error("ERR syntax error");
                    }
                    try {
                        long raw = Long.parseLong(ascii(args[++i]));
                        if (raw <= 0) {
                            return new Reply.Error(
                                    "ERR invalid expire time in 'set' command");
                        }
                        expireAtMillis = switch (opt) {
                            case "EX" -> System.currentTimeMillis() + Math.multiplyExact(raw, 1000L);
                            case "PX" -> System.currentTimeMillis() + raw;
                            case "EXAT"-> Math.multiplyExact(raw, 1000L);
                            case "PXAT" -> raw;
                            default -> throw new IllegalStateException();
                        };
                    } catch (NumberFormatException | ArithmeticException e) {
                        return new Reply.Error("ERR invalid expire time in 'set' command");
                    }
                }
                case "NX" -> {
                    if (nx || xx) return new Reply.Error("ERR syntax error");
                    nx = true;
                }
                case "XX" -> {
                    if (xx || nx) return new Reply.Error("ERR syntax error");
                    xx = true;
                }
                default -> {
                    return new Reply.Error("ERR syntax error");
                }
            }
        }

        boolean set;
        if (nx || xx) {
            set = store.setWithOptions(args[0], new Value.StringValue(args[1]), expireAtMillis, nx, xx);
        } else {
            store.set(args[0], new Value.StringValue(args[1]), expireAtMillis);
            set = true;
        }

        return set ? new Reply.SimpleString("OK") : new Reply.Null();
    }

    @Override
    public boolean isWrite() {
        return true;
    }

    private static String ascii(byte[] b) {
        return new String(b, java.nio.charset.StandardCharsets.US_ASCII);
    }
}