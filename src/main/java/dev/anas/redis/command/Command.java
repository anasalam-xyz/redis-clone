package dev.anas.redis.command;

import dev.anas.redis.protocol.Reply;

public interface Command {
    Reply execute(byte[][] args);

    // True if this command mutates the store : used by AOF to decide what to persist.
    boolean isWrite();
}