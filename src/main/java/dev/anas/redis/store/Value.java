package dev.anas.redis.store;

import java.util.Deque;
import java.util.Map;

public sealed interface Value {

    public record StringValue(byte[] bytes) implements Value {}

    public record ListValue(Deque<byte[]> items) implements Value {}

    public record HashValue(Map<Key, byte[]> fields) implements Value {}
}