package dev.anas.redis.store;

public sealed interface StoreResult<T> {

    record Success<T>(T value) implements StoreResult<T> {};
    record WrongType<T>() implements StoreResult<T> {};
}
