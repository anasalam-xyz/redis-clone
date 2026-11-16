package dev.anas.redis.store;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

public final class Store {
    private final Map<Key, Entry> map = new HashMap<>();
    private final ReentrantLock lock = new ReentrantLock();

    public Value get(byte[] key) {
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

    public void set(byte[] key, Value value, long expireAtMillis) {
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

    public boolean setExpiry(byte[] key, long expireAtMillis) {
        lock.lock();
        try {
            Key k = new Key(key);
            Entry entry = map.get(k);
            if (entry == null || entry.isExpired(System.currentTimeMillis())) {
                map.remove(k);
                return false;
            }

            map.put(k, new Entry(entry.value(), expireAtMillis));
            return true;
        } finally {
            lock.unlock();
        }
    }

    public long getExpiry(byte[] key) {
        lock.lock();
        try {
            Key k = new Key(key);
            Entry entry = map.get(k);
            if (entry == null || entry.isExpired(System.currentTimeMillis())) {
                map.remove(k);
                return -1L;
            }
            return entry.expireAtMillis();
        } finally {
            lock.unlock();
        }
    }

    public boolean persist(byte[] key) {
        lock.lock();
        try {
            Key k = new Key(key);
            Entry e = map.get(k);
            if (e == null || e.isExpired(System.currentTimeMillis())) {
                map.remove(k);
                return false;
            }
            if (e.expireAtMillis() == 0L)
                return false; // no TTL to remove

            map.put(k, new Entry(e.value(), 0L));
            return true;
        } finally {
            lock.unlock();
        }
    }

    public int sweepExpired() {
        lock.lock();
        try {
            long now = System.currentTimeMillis();
            int count = map.size();
            map.entrySet().removeIf(e -> e.getValue().isExpired(now));
            return count - map.size();
        } finally {
            lock.unlock();
        }
    }

    public void setKeepTtl(byte[] key, Value value) {
        lock.lock();
        try {
            Key k = new Key(key);
            Entry e = map.get(k);
            long ttl = (e != null && !e.isExpired(System.currentTimeMillis()))
                    ? e.expireAtMillis()
                    : 0L;
            map.put(k, new Entry(value, ttl));
        } finally {
            lock.unlock();
        }
    }
}
