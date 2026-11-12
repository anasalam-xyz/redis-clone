package dev.anas.redis.store;

import java.util.Arrays;

public record Key(byte[] bytes) {
    @Override
    public boolean equals(Object o) {
        return o instanceof Key k && Arrays.equals(bytes, k.bytes);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(bytes);
    }
}
