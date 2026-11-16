package dev.anas.redis.store;

public record Entry(Value value, long expireAtMillis) {
    // expireAtMillis == 0 means no expiration

    public boolean isExpired(long nowMillis) {
        return expireAtMillis > 0 && nowMillis >= expireAtMillis;
    }
}
