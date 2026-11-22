package dev.anas.redis.store;

import java.util.Comparator;
import java.util.PriorityQueue;

public final class ExpirySweeper {
    private final Store store;
    private final long intervalMillis;
    private final PriorityQueue<ExpiryEntry> heap = new PriorityQueue<>(
            Comparator.comparingLong(ExpiryEntry::expireAt));
    private volatile boolean running = true;

    record ExpiryEntry(byte[] key, long expireAt) {
    }

    public ExpirySweeper(Store store, long intervalMillis) {
        this.store = store;
        this.intervalMillis = intervalMillis;
    }

    public synchronized void schedule(byte[] key, long expireAt) {
        heap.add(new ExpiryEntry(key, expireAt));
    }

    public Thread start() {
        return Thread.ofVirtual().start(this::run);
    }

    public void stop() {
        running = false;
    }

    private void run() {
        while (running) {
            try {
                Thread.sleep(intervalMillis);
            } catch (InterruptedException e) {
                return;
            }
            sweepExpired();
        }
    }

    private void sweepExpired() {
        long now = System.currentTimeMillis();
        for (int budget = 10_000; budget > 0; budget--) {
            ExpiryEntry due;
            synchronized (this) {
                due = heap.peek();
                if (due == null || due.expireAt() > now)
                    return;
                heap.poll();
            }
            store.deleteIfExpiresAt(due.key(), due.expireAt());
        }
    }
}