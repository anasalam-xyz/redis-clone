package dev.anas.redis.protocol;

import java.util.Arrays;

public class RespParser {
    private byte[] buffer = new byte[0]; //save leftover bytes from previous read
    private int pos = 0; 
    
    public ParseResult feed(byte[] chunk) {
        append(chunk);
        int startPos = pos;

        byte[] line = readLine();
        if(line == null) {
            pos = startPos; // Reset position to the start of the chunk
            return new ParseResult.NeedMore();
        }
        if(line.length == 0) {
            return new ParseResult.ParseError("Empty line");
        }
        if(line[0] != '*') {
            return new ParseResult.ParseError("Expected array prefix '*', got: " + (char) line[0]);
        }

        int numArgs = parseInt(line, 1, line.length);
        byte[][] args = new byte[numArgs][];

        for(int i = 0; i < numArgs; i++) {
            byte[] bulkLengthLine = readLine();

            if(bulkLengthLine == null) {
                pos = startPos; // Reset position to the start of the chunk
                return new ParseResult.NeedMore();
            }
            if(bulkLengthLine.length == 0) {
                return new ParseResult.ParseError("Empty line");
            }
            if(bulkLengthLine[0] != '$') {
                return new ParseResult.ParseError("Expected string prefix '$', got: " + (char) bulkLengthLine[0]);
            }

            int bulkLength = parseInt(bulkLengthLine, 1, bulkLengthLine.length);

            if(pos + bulkLength + 2 > buffer.length) { // +2 for \r\n
                pos = startPos; // Reset position to the start of the chunk
                return new ParseResult.NeedMore();
            }

            args[i] = Arrays.copyOfRange(buffer, pos, pos + bulkLength);
            pos += bulkLength + 2; // Move past the bulk string and the \r\n
        }

        return new ParseResult.Parsed(args);
    }

    private void append(byte[] chunk) {
        byte[] newBuffer = new byte[buffer.length + chunk.length];
        System.arraycopy(buffer, 0, newBuffer, 0, buffer.length);
        System.arraycopy(chunk, 0, newBuffer, buffer.length, chunk.length);
        buffer = newBuffer;
    }
    
    private byte[] readLine() {
        for(int i = pos; i < buffer.length - 1; i++) {
            if(buffer[i] == '\r' && buffer[i + 1] == '\n') {
                byte[] line = Arrays.copyOfRange(buffer, pos, i);
                pos = i + 2; // Move past the \r\n
                return line;
            }
        }

        return null; // No complete line found
    }

    private int parseInt(byte[] line, int start, int end) {
        int result = 0;

        for(int i = start; i < end; i++) {
            result = result * 10 + (line[i] - '0');
        }

        return result;
    }
}
