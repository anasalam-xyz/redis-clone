package dev.anas.redis.store;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
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

    public StoreResult<Long> listPushAll(byte[] key, byte[][] elements, boolean left) {
        lock.lock();
        try {
            Key k = new Key(key);
            Entry e = map.get(k);

            if (e == null || e.isExpired(System.currentTimeMillis())) {
                Deque<byte[]> newList = new ArrayDeque<>();
                for (byte[] element : elements) {
                    if (left)
                        newList.addFirst(element);
                    else
                        newList.addLast(element);
                }
                map.put(k, new Entry(new Value.ListValue(newList), 0L));
                return new StoreResult.Success<>((long) newList.size());
            }

            Value value = e.value();
            if (!(value instanceof Value.ListValue lv)) {
                return new StoreResult.WrongType<>();
            }

            Deque<byte[]> list = lv.items();
            for (byte[] element : elements) {
                if (left)
                    list.addFirst(element);
                else
                    list.addLast(element);
            }

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

            Value value = e.value();
            if (!(value instanceof Value.ListValue lv)) {
                return new StoreResult.WrongType<>();
            }

            Deque<byte[]> list = lv.items();
            byte[] element = left ? list.pollFirst() : list.pollLast();

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

            Value value = e.value();
            if (!(value instanceof Value.ListValue lv)) {
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
                return new StoreResult.Success<>(List.of()); // List.of() is a shared singleton. Thats why it was chosen
                                                             // over new ArrayList>() which allocated a new list.
            }

            Value value = e.value();
            if (!(value instanceof Value.ListValue lv)) {
                return new StoreResult.WrongType<>();
            }

            Deque<byte[]> items = lv.items();
            int size = items.size();
            if (size == 0) {
                return new StoreResult.Success<>(List.of());
            }

            if (start < 0)
                start += size;
            if (stop < 0)
                stop += size;
            if (start < 0)
                start = 0;
            if (stop >= size)
                stop = size - 1;
            if (start > stop || start >= size) {
                return new StoreResult.Success<>(List.of());
            }

            List<byte[]> result = new ArrayList<>();
            int i = 0;
            for (byte[] element : items) {
                if (i > stop)
                    break;
                if (i >= start)
                    result.add(element);
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

            if (e == null || e.isExpired(System.currentTimeMillis())) {
                e = new Entry(new Value.HashValue(new HashMap<>()), 0L);
                map.put(k, e);
            }

            if (!(e.value() instanceof Value.HashValue hv)) {
                return new StoreResult.WrongType<>();
            }

            int newFields = 0;
            Map<Key, byte[]> hashVal = hv.fields();
            for (int i = 0; i < fieldValuePairs.length; i += 2) {
                Key field = new Key(fieldValuePairs[i]);
                byte[] val = fieldValuePairs[i + 1];
                
                if(!hashVal.containsKey(field)) 
                    newFields++;
                hashVal.put(field, val);
            }

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

            Map<Key, byte[]> hashVal = hv.fields();
            int count = hashVal.size();

            for(byte[] field: fields) {
                hashVal.remove(new Key(field));
            }

            count -= hashVal.size();
            if(hashVal.isEmpty()) {
                map.remove(k);
            }

            return new StoreResult.Success<>((long) count);
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
