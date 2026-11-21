package dev.anas.redis.store;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.locks.ReentrantLock;

public final class Store {
    private final ReentrantLock lock = new ReentrantLock();
    private final Map<Key, Entry> map = new LinkedHashMap<>(16, 0.75f, true);
    private long maxMemory = 0, currentMemory = 0;

    public void setMaxMemory(long maxMemory) {
        lock.lock();
        try {
            this.maxMemory = maxMemory;
        } finally {
            lock.unlock();
        }
    }

    private static long sizeOf(Entry entry) {
        Value v = entry.value();
        return switch (v) {
            case Value.StringValue s -> s.bytes().length;
            case Value.ListValue l -> {
                long sum = 0;
                for (byte[] e : l.items()) sum += e.length;
                yield sum;
            }
            case Value.HashValue h -> {
                long sum = 0;
                for (var e : h.fields().entrySet())
                    sum += e.getKey().bytes().length + e.getValue().length;
                yield sum;
            }
        };
    }

    private void evictIfNeeded() {
        if (maxMemory <= 0) return;
        while (currentMemory > maxMemory && !map.isEmpty()) {
            Map.Entry<Key, Entry> lru = map.entrySet().iterator().next();
            currentMemory -= sizeOf(lru.getValue());
            map.remove(lru.getKey());
        }
    }

    public Value get(byte[] key) {
        lock.lock();
        try {
            Key k = new Key(key);
            Entry entry = map.get(k);
            if (entry == null) return null;
            if (entry.isExpired(System.currentTimeMillis())) {
                currentMemory -= sizeOf(entry);
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
            Key k = new Key(key);
            Entry old = map.get(k);
            if (old != null) currentMemory -= sizeOf(old);
            Entry e = new Entry(value, expireAtMillis);
            map.put(k, e);
            currentMemory += sizeOf(e);
            evictIfNeeded();
        } finally {
            lock.unlock();
        }
    }

    public boolean del(byte[] key) {
        lock.lock();
        try {
            Entry e = map.remove(new Key(key));
            if (e != null) currentMemory -= sizeOf(e);
            return e != null;
        } finally {
            lock.unlock();
        }
    }

    public boolean exists(byte[] key) {
        return get(key) != null;
    }

    public boolean setExpiry(byte[] key, long expireAtMillis) {
        lock.lock();
        try {
            Key k = new Key(key);
            Entry old = map.get(k);
            if (old == null || old.isExpired(System.currentTimeMillis())) {
                map.remove(k);
                return false;
            }
            currentMemory -= sizeOf(old);
            Entry e = new Entry(old.value(), expireAtMillis);
            map.put(k, e);
            currentMemory += sizeOf(e);
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
            if (entry == null) return -1L;
            if (entry.isExpired(System.currentTimeMillis())) {
                currentMemory -= sizeOf(entry);
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
            Entry old = map.get(k);
            if (old == null || old.isExpired(System.currentTimeMillis())) {
                map.remove(k);
                return false;
            }
            if (old.expireAtMillis() == 0L) return false;
            currentMemory -= sizeOf(old);
            Entry e = new Entry(old.value(), 0L);
            map.put(k, e);
            currentMemory += sizeOf(e);
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
            map.entrySet().removeIf(e -> {
                Entry entry = e.getValue();
                if (entry.isExpired(now)) {
                    currentMemory -= sizeOf(entry);
                    return true;
                }
                return false;
            });
            return count - map.size();
        } finally {
            lock.unlock();
        }
    }

    public void setKeepTtl(byte[] key, Value value) {
        lock.lock();
        try {
            Key k = new Key(key);
            Entry old = map.get(k);
            if (old != null) currentMemory -= sizeOf(old);
            long ttl = (old != null && !old.isExpired(System.currentTimeMillis()))
                    ? old.expireAtMillis()
                    : 0L;
            Entry e = new Entry(value, ttl);
            map.put(k, e);
            currentMemory += sizeOf(e);
            evictIfNeeded();
        } finally {
            lock.unlock();
        }
    }

    public StoreResult<Long> listPushAll(byte[] key, byte[][] elements, boolean left) {
        lock.lock();
        try {
            Key k = new Key(key);
            Entry old = map.get(k);

            if (old == null || old.isExpired(System.currentTimeMillis())) {
                Deque<byte[]> newList = new ArrayDeque<>();
                for (byte[] element : elements) {
                    if (left) newList.addFirst(element);
                    else      newList.addLast(element);
                }
                Entry e = new Entry(new Value.ListValue(newList), 0L);
                map.put(k, e);
                currentMemory += sizeOf(e);
                evictIfNeeded();
                return new StoreResult.Success<>((long) newList.size());
            }

            if (!(old.value() instanceof Value.ListValue lv)) {
                return new StoreResult.WrongType<>();
            }

            currentMemory -= sizeOf(old);
            Deque<byte[]> list = lv.items();
            for (byte[] element : elements) {
                if (left) list.addFirst(element);
                else      list.addLast(element);
            }
            currentMemory += sizeOf(old);
            evictIfNeeded();

            return new StoreResult.Success<>((long) list.size());
        } finally {
            lock.unlock();
        }
    }

    public StoreResult<byte[]> listPop(byte[] key, boolean left) {
        lock.lock();
        try {
            Key k = new Key(key);
            Entry e = map.get(k);

            if (e == null || e.isExpired(System.currentTimeMillis())) {
                return new StoreResult.Success<>(null);
            }
            if (!(e.value() instanceof Value.ListValue lv)) {
                return new StoreResult.WrongType<>();
            }

            Deque<byte[]> list = lv.items();
            byte[] element = left ? list.pollFirst() : list.pollLast();
            if (element != null) currentMemory -= element.length;
            if (list.isEmpty()) {
                map.remove(k);
            }
            return new StoreResult.Success<>(element);
        } finally {
            lock.unlock();
        }
    }

    public StoreResult<Long> listLen(byte[] key) {
        lock.lock();
        try {
            Key k = new Key(key);
            Entry e = map.get(k);
            if (e == null || e.isExpired(System.currentTimeMillis())) {
                return new StoreResult.Success<>(0L);
            }
            if (!(e.value() instanceof Value.ListValue lv)) {
                return new StoreResult.WrongType<>();
            }
            return new StoreResult.Success<>((long) lv.items().size());
        } finally {
            lock.unlock();
        }
    }

    public StoreResult<List<byte[]>> listRange(byte[] key, long start, long stop) {
        lock.lock();
        try {
            Key k = new Key(key);
            Entry e = map.get(k);
            if (e == null || e.isExpired(System.currentTimeMillis())) {
                return new StoreResult.Success<>(List.of());
            }
            if (!(e.value() instanceof Value.ListValue lv)) {
                return new StoreResult.WrongType<>();
            }

            Deque<byte[]> items = lv.items();
            int size = items.size();
            if (size == 0) return new StoreResult.Success<>(List.of());

            if (start < 0) start += size;
            if (stop  < 0) stop  += size;
            if (start < 0) start = 0;
            if (stop >= size) stop = size - 1;
            if (start > stop || start >= size) {
                return new StoreResult.Success<>(List.of());
            }

            List<byte[]> result = new ArrayList<>();
            int i = 0;
            for (byte[] element : items) {
                if (i > stop) break;
                if (i >= start) result.add(element);
                i++;
            }
            return new StoreResult.Success<>(result);
        } finally {
            lock.unlock();
        }
    }

    public StoreResult<Long> hashSet(byte[] key, byte[][] fieldValuePairs) {
        lock.lock();
        try {
            Key k = new Key(key);
            Entry e = map.get(k);
            Value.HashValue hv;

            if (e == null || e.isExpired(System.currentTimeMillis())) {
                hv = new Value.HashValue(new HashMap<>());
                e = new Entry(hv, 0L);
                map.put(k, e);
            } else if (e.value() instanceof Value.HashValue existing) {
                hv = existing;
            } else {
                return new StoreResult.WrongType<>();
            }

            long delta = 0;
            int newFields = 0;
            Map<Key, byte[]> fields = hv.fields();
            for (int i = 0; i < fieldValuePairs.length; i += 2) {
                Key field = new Key(fieldValuePairs[i]);
                byte[] val = fieldValuePairs[i + 1];
                byte[] oldVal = fields.get(field);
                if (oldVal == null) newFields++;
                else delta -= field.bytes().length + oldVal.length;
                delta += field.bytes().length + val.length;
                fields.put(field, val);
            }
            currentMemory += delta;
            evictIfNeeded();

            return new StoreResult.Success<>((long) newFields);
        } finally {
            lock.unlock();
        }
    }

    public StoreResult<byte[]> hashGet(byte[] key, byte[] field) {
        lock.lock();
        try {
            Key k = new Key(key);
            Entry e = map.get(k);
            if (e == null || e.isExpired(System.currentTimeMillis())) {
                return new StoreResult.Success<>(null);
            }
            if (!(e.value() instanceof Value.HashValue hv)) {
                return new StoreResult.WrongType<>();
            }
            return new StoreResult.Success<>(hv.fields().get(new Key(field)));
        } finally {
            lock.unlock();
        }
    }

    public StoreResult<Long> hashDel(byte[] key, byte[][] fields) {
        lock.lock();
        try {
            Key k = new Key(key);
            Entry e = map.get(k);
            if (e == null || e.isExpired(System.currentTimeMillis())) {
                return new StoreResult.Success<>(0L);
            }
            if (!(e.value() instanceof Value.HashValue hv)) {
                return new StoreResult.WrongType<>();
            }

            Map<Key, byte[]> mapFields = hv.fields();
            long delta = 0;
            int removed = 0;
            for (byte[] field : fields) {
                Key f = new Key(field);
                byte[] oldVal = mapFields.remove(f);
                if (oldVal != null) {
                    delta -= f.bytes().length + oldVal.length;
                    removed++;
                }
            }
            currentMemory += delta;
            if (mapFields.isEmpty()) {
                map.remove(k);
            }
            return new StoreResult.Success<>((long) removed);
        } finally {
            lock.unlock();
        }
    }

    public StoreResult<Map<Key, byte[]>> hashGetAll(byte[] key) {
        lock.lock();
        try {
            Key k = new Key(key);
            Entry e = map.get(k);
            if (e == null || e.isExpired(System.currentTimeMillis())) {
                return new StoreResult.Success<>(Map.of());
            }
            if (!(e.value() instanceof Value.HashValue hv)) {
                return new StoreResult.WrongType<>();
            }
            return new StoreResult.Success<>(new HashMap<>(hv.fields()));
        } finally {
            lock.unlock();
        }
    }

    public StoreResult<Boolean> hashExists(byte[] key, byte[] field) {
        lock.lock();
        try {
            Key k = new Key(key);
            Entry e = map.get(k);
            if (e == null || e.isExpired(System.currentTimeMillis())) {
                return new StoreResult.Success<>(false);
            }
            if (!(e.value() instanceof Value.HashValue hv)) {
                return new StoreResult.WrongType<>();
            }
            return new StoreResult.Success<>(hv.fields().containsKey(new Key(field)));
        } finally {
            lock.unlock();
        }
    }
}