package dev.anas.redis.protocol;

import java.util.List;

public sealed interface Reply {
    
    record BulkString(byte[] value) implements Reply {}
    record Array(List<Reply> values) implements Reply {}
}
