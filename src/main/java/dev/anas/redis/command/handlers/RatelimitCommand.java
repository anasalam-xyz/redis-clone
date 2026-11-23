package dev.anas.redis.command.handlers;

import dev.anas.redis.command.Command;
import dev.anas.redis.protocol.Reply;
import dev.anas.redis.ratelimit.RateLimiter;
import java.nio.charset.StandardCharsets;

public final class RatelimitCommand implements Command {
    private final RateLimiter limiter;

    public RatelimitCommand(RateLimiter limiter) {
        this.limiter = limiter;
    }

    @Override
    public Reply execute(byte[][] args) {
        if (args.length != 3) {
            return new Reply.Error("ERR wrong number of arguments for 'ratelimit' command");
        }

        int capacity;
        try {
            capacity = Integer.parseInt(new String(args[1], StandardCharsets.US_ASCII));
            if (capacity <= 0) {
                return new Reply.Error("ERR capacity must be a positive integer");
            }
        } catch (NumberFormatException e) {
            return new Reply.Error("ERR value is not an integer or out of range");
        }

        double refill;
        try {
            refill = Double.parseDouble(new String(args[2], StandardCharsets.US_ASCII));
            if (!Double.isFinite(refill) || refill <= 0) {
                return new Reply.Error("ERR refill must be a positive finite number");
            }
        } catch (NumberFormatException e) {
            return new Reply.Error("ERR value is not a float or out of range");
        }

        long nowMs = System.currentTimeMillis();
        boolean allowed = limiter.tryAcquire(args[0], capacity, refill, nowMs);

        return new Reply.Integer(allowed ? 1 : 0);
    }

    @Override
    public boolean isWrite() {
        return false;
    }
}
