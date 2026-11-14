package dev.anas.redis.store;

public final class ExpirySweeper {

    private final Store store;
    private final long intervalMillis;
    private volatile boolean running = true;

    public ExpirySweeper(Store store, long intervalMillis) {
        this.store = store;
        this.intervalMillis = intervalMillis;
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
            int swept = store.sweepExpired();
            if (swept > 0) {
                System.err.println("expiry sweep: removed " + swept + " keys");
            }
        }
    }
}