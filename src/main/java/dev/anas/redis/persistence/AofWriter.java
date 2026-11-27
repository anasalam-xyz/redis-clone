package dev.anas.redis.persistence;

import dev.anas.redis.protocol.RespWriter;

import java.io.BufferedOutputStream;
import java.nio.file.StandardCopyOption;
import java.util.Iterator;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.FileDescriptor;

public final class AofWriter implements AutoCloseable {

    private final Path path;
    private FileOutputStream fos;
    private BufferedOutputStream buf;
    private final Thread flushThread;
    private long baseSize;

    public AofWriter(String p) throws IOException {
        this.path = Path.of(p);
        Path parent = path.getParent();

        if (parent != null) {
            Files.createDirectories(parent);
        }
        this.fos = new FileOutputStream(p, true);
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

    public synchronized void rewrite(Iterator<byte[][]> snapshotCommands) throws IOException {
        buf.flush();
        Path tmp = path.resolveSibling(path.getFileName() + ".rewrite");

        try (FileOutputStream out = new FileOutputStream(tmp.toFile());
                BufferedOutputStream bout = new BufferedOutputStream(out, 1 << 16)) {
            while (snapshotCommands.hasNext()) {
                RespWriter.writeCommand(bout, snapshotCommands.next());
            }
            bout.flush();
            out.getFD().sync();
        }

        buf.close();
        try {
            Files.move(tmp, path, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            // reopen whatever is at `path` now (new file on success, old file if the move failed)
            this.fos = new FileOutputStream(path.toFile(), true);
            this.buf = new BufferedOutputStream(fos);
        }
        this.baseSize = Files.size(path);
    }

    public synchronized boolean shouldRewrite() throws IOException {
        long size = Files.size(path);
        return size > 64L * 1024 * 1024 && size > baseSize * 2;
    }

    public void append(byte[][] args) throws IOException {
        synchronized (this) {
            RespWriter.writeCommand(buf, args);
        }
    }

    public void fsync() throws IOException {
        FileDescriptor fd;
        synchronized (this) {
            buf.flush(); // Java buffer -> OS
            fd = fos.getFD(); // grab the current file descriptor
        }
        fd.sync(); // OS -> disk
    }

    @Override
    public synchronized void close() throws IOException {
        flushThread.interrupt();
        buf.close(); // flushes and closes internally
    }
}