package dev.anas.redis.protocol;

import java.util.List;

public sealed interface Reply {
    record SimpleString(String s) implements Reply {}   // +<s>\r\n
    record Error(String msg) implements Reply {}   // -<msg>\r\n
    record Integer(long n) implements Reply {}   // :<n>\r\n
    record BulkString(byte[] data) implements Reply {}   // $<len>\r\n<data>\r\n
    record Null() implements Reply {}   // $-1\r\n
    record Array(List<Reply> items) implements Reply {}  // *<n>\r\n<items...>
}
