package dev.anas.redis.command.handlers;

import java.nio.charset.StandardCharsets;

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
                            case "EXAT" -> Math.multiplyExact(raw, 1000L);
                            case "PXAT" -> raw;
                            default -> throw new IllegalStateException();
                        };
                    } catch (NumberFormatException | ArithmeticException e) {
                        return new Reply.Error("ERR invalid expire time in 'set' command");
                    }
                }
                case "NX" -> {
                    if (nx || xx)
                        return new Reply.Error("ERR syntax error");
                    nx = true;
                }
                case "XX" -> {
                    if (xx || nx)
                        return new Reply.Error("ERR syntax error");
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

    @Override
    public byte[][] toAofArgs(byte[][] fullArgs) {
        byte[][] out = fullArgs.clone();
        long now = System.currentTimeMillis();
        for (int i = 3; i < out.length; i++) {
            String opt = ascii(out[i]).toUpperCase(java.util.Locale.ROOT);
            switch (opt) {
                case "EX", "PX", "EXAT" -> {
                    long raw = Long.parseLong(ascii(out[i + 1]));
                    long at = switch (opt) {
                        case "EX" -> now + raw * 1000L;
                        case "PX" -> now + raw;
                        default -> raw * 1000L; // EXAT
                    };
                    out[i] = "PXAT".getBytes(StandardCharsets.US_ASCII);
                    out[i + 1] = Long.toString(at).getBytes(StandardCharsets.US_ASCII);
                    i++; // skip the value we just replaced
                }
                case "PXAT" -> i++; // already absolute
                default -> {
                } // NX / XX stay as they are
            }
        }
        return out;
    }
}