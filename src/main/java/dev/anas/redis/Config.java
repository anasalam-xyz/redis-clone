package dev.anas.redis;

import java.nio.file.Path;

public record Config(int port, boolean appendOnly, Path aofPath, long maxMemory) {
    public static Config parse(String[] args) {
        int port = 6380;
        boolean appendOnly = false;
        Path aofPath = Path.of("appendonly.aof");
        long maxMemory = 0; // 0 = unlimited

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--port" -> port = Integer.parseInt(value(args, ++i));
                case "--appendonly" -> appendOnly = yesNo(value(args, ++i));
                case "--appendfile" -> aofPath = Path.of(value(args, ++i));
                case "--maxmemory" -> maxMemory = Long.parseLong(value(args, ++i));
                default -> throw new IllegalArgumentException("Unknown option: " + args[i]);
            }
        }
        return new Config(port, appendOnly, aofPath, maxMemory);
    }

    private static String value(String[] a, int i) {
        if (i >= a.length)
            throw new IllegalArgumentException("Missing value for " + a[i - 1]);
        return a[i];
    }

    private static boolean yesNo(String v) {
        return switch (v.toLowerCase()) {
            case "yes", "true", "on" -> true;
            case "no", "false", "off" -> false;
            default -> throw new IllegalArgumentException("Expected yes/no, got: " + v);
        };
    }
}