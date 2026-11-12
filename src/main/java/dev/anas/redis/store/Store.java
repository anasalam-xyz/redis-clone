package dev.anas.redis.store;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

public final class Store {
    private final Map<Key, Entry> map = new HashMap<>();
    private final ReentrantLock lock = new ReentrantLock();

    public byte[] get(byte[] key) {
        lock.lock();
        try {
            Key k = new Key(key);
            Entry entry = map.get(k);
            if (entry == null) {
                return null;
            }
            if (entry.isExpired(System.currentTimeMillis())) {
                map.remove(k);
                return null;
            }
            return entry.value();
        } finally {
            lock.unlock();
        }
    }

    public void set(byte[] key, byte[] value, long expireAtMillis) {
        lock.lock();
        try {
            map.put(new Key(key), new Entry(value, expireAtMillis));
        } finally {
            lock.unlock();
        }
    }

    public boolean del(byte[] key) {
        lock.lock();
        try {
            return map.remove(new Key(key)) != null;
        } finally {
            lock.unlock();
        }
    }

    public boolean exists(byte[] key) {
        return get(key) != null; // to wire in expiration logic, calling get()
    }
}
