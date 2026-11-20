package dev.anas.redis.persistence;

import dev.anas.redis.protocol.RespWriter;
import dev.anas.redis.protocol.Reply;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.ArrayList;

public final class AofWriter implements AutoCloseable {

    private final FileOutputStream fos;
    private final BufferedOutputStream buf;
    private final Thread flushThread;

    public AofWriter(String path) throws IOException {
        Path filePath = Path.of(path);
        Path parent = filePath.getParent();

        if (parent != null) {
            Files.createDirectories(parent);
        }
        this.fos = new FileOutputStream(path, true);
        this.buf = new BufferedOutputStream(fos);

        this.flushThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(1000);
                    this.fsync();
                } catch (InterruptedException e) {
                    return;
                } catch (IOException e) {
                    System.err.println("AOF fsync failed: " + e.getMessage());
                }
            }
        });
        this.flushThread.setDaemon(true);
        this.flushThread.start();
    }

    public synchronized void append(byte[][] args) throws IOException {
        List<Reply> list = new ArrayList<>();

        for(byte[] arg: args) {
            list.add(new Reply.BulkString(arg));
        }
        
        buf.write(RespWriter.write(new Reply.Array(list)));
    }

    public synchronized void fsync() throws IOException {
        buf.flush(); // Java buffer -> OS
        fos.getFD().sync(); // OS -> disk
    }

    @Override
    public synchronized void close() throws IOException {
        flushThread.interrupt();
        buf.close(); // flushes and closes internally
    }
}