package io.helix.storage;

import io.helix.core.Key;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

final class Segment {

    private static final int ENTRY_OVERHEAD = 64;

    final ConcurrentHashMap<Key, byte[]> data = new ConcurrentHashMap<>();
    private final AtomicLong bytes = new AtomicLong();

    Optional<byte[]> get(Key key) {
        return Optional.ofNullable(data.get(key));
    }

    void set(Key key, byte[] value) {
        byte[] previous = data.put(key, value);
        long delta = entryBytes(key, value);
        if (previous != null) {
            delta -= entryBytes(key, previous);
        }
        bytes.addAndGet(delta);
    }

    boolean delete(Key key) {
        byte[] removed = data.remove(key);
        if (removed == null) {
            return false;
        }
        bytes.addAndGet(-entryBytes(key, removed));
        return true;
    }

    boolean exists(Key key) {
        return data.containsKey(key);
    }

    long keyCount() {
        return data.size();
    }

    long usedBytes() {
        return bytes.get();
    }

    static long entryBytes(Key key, byte[] value) {
        return ENTRY_OVERHEAD + key.length() + value.length;
    }
}
