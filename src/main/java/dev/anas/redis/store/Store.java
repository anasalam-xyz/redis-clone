package dev.anas.redis.store;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.locks.ReentrantLock;
import java.util.Iterator;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

public final class Store {
    private final ReentrantLock lock = new ReentrantLock();
    private final Map<Key, Entry> map;
    private final long maxMemory;
    private long currentMemory = 0;
    private ExpirySweeper sweeper;

    private static final int ITEMS_PER_COMMAND = 64; // same chunk size real Redis uses
    private static final byte[] CMD_SET = ascii("SET");
    private static final byte[] CMD_RPUSH = ascii("RPUSH");
    private static final byte[] CMD_HSET = ascii("HSET");
    private static final byte[] CMD_PEXPIREAT = ascii("PEXPIREAT");

    private volatile Consumer<byte[]> evictionListener;
    private volatile boolean evictionPaused;

    public void setEvictionListener(Consumer<byte[]> listener) {
        this.evictionListener = listener;
    }

    public void setEvictionPaused(boolean paused) {
        this.evictionPaused = paused;
    }

    public Store() {
        this(0);
    }

    public void setSweeper(ExpirySweeper sweeper) {
        this.sweeper = sweeper;
    }

    public Store(long maxMemory) {
        this.maxMemory = maxMemory;
        this.map = new LinkedHashMap<>(16, 0.75f, maxMemory > 0); // access order only if evicting
    }

    private static byte[] ascii(String s) {
        return s.getBytes(StandardCharsets.US_ASCII);
    }

    public Iterator<byte[][]> snapshotAsCommands() {
        long now = System.currentTimeMillis();
        List<Map.Entry<Key, Entry>> live;

        lock.lock();
        try {
            live = new ArrayList<>(map.size());
            for (Map.Entry<Key, Entry> e : map.entrySet()) {
                if (!e.getValue().isExpired(now)) {
                    live.add(Map.entry(e.getKey(), e.getValue()));
                }
            }
        } finally {
            lock.unlock();
        }

        Iterator<Map.Entry<Key, Entry>> source = live.iterator();
        Deque<byte[][]> pending = new ArrayDeque<>();

        return new Iterator<>() {
            @Override
            public boolean hasNext() {
                while (pending.isEmpty() && source.hasNext()) {
                    Map.Entry<Key, Entry> e = source.next();
                    emit(e.getKey().bytes(), e.getValue(), pending);
                }
                return !pending.isEmpty();
            }

            @Override
            public byte[][] next() {
                if (!hasNext())
                    throw new NoSuchElementException();
                return pending.poll();
            }
        };
    }

    private static void emit(byte[] key, Entry entry, Deque<byte[][]> out) {
        switch (entry.value()) {
            case Value.StringValue s -> out.add(new byte[][] { CMD_SET, key, s.bytes() });

            case Value.ListValue l -> {
                List<byte[]> batch = new ArrayList<>(ITEMS_PER_COMMAND);
                for (byte[] item : l.items()) { // head to tail, so RPUSH rebuilds the same order
                    batch.add(item);
                    if (batch.size() == ITEMS_PER_COMMAND) {
                        out.add(command(CMD_RPUSH, key, batch));
                        batch.clear();
                    }
                }
                if (!batch.isEmpty())
                    out.add(command(CMD_RPUSH, key, batch));
            }

            case Value.HashValue h -> {
                List<byte[]> batch = new ArrayList<>(ITEMS_PER_COMMAND * 2);
                int fields = 0;
                for (Map.Entry<Key, byte[]> f : h.fields().entrySet()) {
                    batch.add(f.getKey().bytes());
                    batch.add(f.getValue());
                    if (++fields == ITEMS_PER_COMMAND) {
                        out.add(command(CMD_HSET, key, batch));
                        batch.clear();
                        fields = 0;
                    }
                }
                if (!batch.isEmpty())
                    out.add(command(CMD_HSET, key, batch));
            }
        }

        long expireAt = entry.expireAtMillis();
        if (expireAt != 0L) {
            out.add(new byte[][] { CMD_PEXPIREAT, key, ascii(Long.toString(expireAt)) });
        }
    }

    private static byte[][] command(byte[] name, byte[] key, List<byte[]> args) {
        byte[][] cmd = new byte[2 + args.size()][];
        cmd[0] = name;
        cmd[1] = key;
        for (int i = 0; i < args.size(); i++)
            cmd[2 + i] = args.get(i);
        return cmd;
    }

    private static long sizeOf(Entry entry) {
        Value v = entry.value();
        return switch (v) {
            case Value.StringValue s -> s.bytes().length;
            case Value.ListValue l -> {
                long sum = 0;
                for (byte[] e : l.items())
                    sum += e.length;
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
        if (maxMemory <= 0 || evictionPaused)
            return;
        while (currentMemory > maxMemory && !map.isEmpty()) {
            Map.Entry<Key, Entry> lru = map.entrySet().iterator().next();
            currentMemory -= sizeOf(lru.getValue());
            map.remove(lru.getKey());
            Consumer<byte[]> listener = evictionListener;
            if (listener != null)
                listener.accept(lru.getKey().bytes());
        }
    }

    public boolean deleteIfExpiresAt(byte[] key, long expireAt) {
        lock.lock();
        try {
            Key k = new Key(key);
            Entry e = map.get(k);
            if (e == null || e.expireAtMillis() != expireAt)
                return false; // gone, or TTL changed: stale record
            currentMemory -= sizeOf(e);
            map.remove(k);
            return true;
        } finally {
            lock.unlock();
        }
    }

    public int size() {
        lock.lock();
        try {
            return map.size();
        } finally {
            lock.unlock();
        }
    }

    public Value get(byte[] key) {
        lock.lock();
        try {
            Key k = new Key(key);
            Entry entry = map.get(k);
            if (entry == null)
                return null;
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
            if (old != null)
                currentMemory -= sizeOf(old);
            Entry e = new Entry(value, expireAtMillis);
            map.put(k, e);
            currentMemory += sizeOf(e);
            evictIfNeeded();
            if (expireAtMillis != 0L && sweeper != null) {
                sweeper.schedule(key, expireAtMillis);
            }
        } finally {
            lock.unlock();
        }
    }

    public boolean setWithOptions(byte[] key, Value value, long expireAtMillis,
            boolean nx, boolean xx) {
        lock.lock();
        try {
            Key k = new Key(key);
            Entry old = map.get(k);

            // Treat an expired entry as absent
            boolean exists = (old != null && !old.isExpired(System.currentTimeMillis()));

            if (nx && exists) {
                return false;
            }
            if (xx && !exists) {
                return false;
            }

            if (old != null) {
                currentMemory -= sizeOf(old);
            }
            Entry e = new Entry(value, expireAtMillis);
            map.put(k, e);
            currentMemory += sizeOf(e);
            evictIfNeeded();

            if (expireAtMillis != 0L && sweeper != null) {
                sweeper.schedule(key, expireAtMillis);
            }
            return true;
        } finally {
            lock.unlock();
        }
    }

    public boolean del(byte[] key) {
        lock.lock();
        try {
            Entry e = map.remove(new Key(key));
            if (e != null)
                currentMemory -= sizeOf(e);
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
                if (old != null) {
                    currentMemory -= sizeOf(old);
                    map.remove(k);
                }
                return false;
            }
            Entry e = new Entry(old.value(), expireAtMillis);
            map.put(k, e);
            if (expireAtMillis != 0L && sweeper != null) {
                sweeper.schedule(key, expireAtMillis);
            }
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
            if (entry == null)
                return -1L;
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
                if (old != null) {
                    currentMemory -= sizeOf(old);
                    map.remove(k);
                }
                return false;
            }
            if (old.expireAtMillis() == 0L) {
                return false;
            }
            Entry e = new Entry(old.value(), 0L);
            map.put(k, e);
            return true;
        } finally {
            lock.unlock();
        }
    }

    public void setKeepTtl(byte[] key, Value value) {
        lock.lock();
        try {
            Key k = new Key(key);
            Entry old = map.get(k);
            if (old != null)
                currentMemory -= sizeOf(old);
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
            boolean fresh = old == null || old.isExpired(System.currentTimeMillis());

            if (!fresh && !(old.value() instanceof Value.ListValue)) {
                return new StoreResult.WrongType<>();
            }

            Deque<byte[]> list;
            if (fresh) {
                if (old != null)
                    currentMemory -= sizeOf(old); // replacing an expired entry
                list = new ArrayDeque<>();
                map.put(k, new Entry(new Value.ListValue(list), 0L));
            } else {
                list = ((Value.ListValue) old.value()).items();
            }

            long added = 0;
            for (byte[] element : elements) {
                if (left)
                    list.addFirst(element);
                else
                    list.addLast(element);
                added += element.length;
            }
            currentMemory += added;
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
            if (element != null)
                currentMemory -= element.length;
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
            if (size == 0)
                return new StoreResult.Success<>(List.of());

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
            Value.HashValue hv;

            if (e == null || e.isExpired(System.currentTimeMillis())) {
                if (e != null) {
                    currentMemory -= sizeOf(e);
                }
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
                if (oldVal == null)
                    newFields++;
                else
                    delta -= field.bytes().length + oldVal.length;
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