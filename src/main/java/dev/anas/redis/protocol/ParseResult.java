package dev.anas.redis.protocol;

public sealed interface ParseResult {
    
    record Parsed(byte[][] args) implements ParseResult {}
    record NeedMore() implements ParseResult {}
    record ParseError(String message) implements ParseResult {}
}
