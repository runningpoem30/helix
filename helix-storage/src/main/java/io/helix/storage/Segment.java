package io.helix.storage;

import io.helix.core.Key;
import io.helix.eviction.EvictionPolicy;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;

final class Segment {

    private static final int ENTRY_OVERHEAD = 64;
    private static final int EVICTION_BATCH = 16;

    final ConcurrentHashMap<Key, CacheEntry> data = new ConcurrentHashMap<>();
    final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    final EvictionPolicy eviction;

    private final AtomicLong bytes = new AtomicLong();
    private final long maxBytes;

    record SetResult(boolean isNew, List<Key> evictedKeys) {}

    Segment(EvictionPolicy eviction, long maxBytesPerSegment) {
        this.eviction = eviction;
        this.maxBytes = maxBytesPerSegment;
    }

    Optional<CacheEntry> getEntry(Key key) {
        return Optional.ofNullable(data.get(key));
    }

    SetResult set(Key key, CacheEntry entry) {
        lock.writeLock().lock();
        try {
            List<Key> evicted = ensureCapacity(key, entryBytes(key, entry.value()));
            CacheEntry previous = data.put(key, entry);
            long delta = entryBytes(key, entry.value());
            if (previous != null) {
                delta -= entryBytes(key, previous.value());
                eviction.onRemove(key);
            }
            bytes.addAndGet(delta);
            eviction.onInsert(key);
            return new SetResult(previous == null, evicted);
        } finally {
            lock.writeLock().unlock();
        }
    }

    void updateEntry(Key key, CacheEntry entry) {
        lock.writeLock().lock();
        try {
            CacheEntry previous = data.put(key, entry);
            if (previous != null) {
                long delta = entryBytes(key, entry.value()) - entryBytes(key, previous.value());
                bytes.addAndGet(delta);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    boolean delete(Key key) {
        lock.writeLock().lock();
        try {
            CacheEntry removed = data.remove(key);
            if (removed == null) {
                return false;
            }
            bytes.addAndGet(-entryBytes(key, removed.value()));
            eviction.onRemove(key);
            return true;
        } finally {
            lock.writeLock().unlock();
        }
    }

    CacheEntry removeForExpiration(Key key) {
        lock.writeLock().lock();
        try {
            CacheEntry removed = data.remove(key);
            if (removed != null) {
                bytes.addAndGet(-entryBytes(key, removed.value()));
                eviction.onRemove(key);
            }
            return removed;
        } finally {
            lock.writeLock().unlock();
        }
    }

    void touch(Key key) {
        lock.readLock().lock();
        try {
            eviction.onAccess(key);
        } finally {
            lock.readLock().unlock();
        }
    }

    long keyCount() {
        return data.size();
    }

    long usedBytes() {
        return bytes.get();
    }

    private List<Key> ensureCapacity(Key inserting, long incoming) {
        List<Key> evicted = new ArrayList<>();
        while (maxBytes > 0 && bytes.get() + incoming > maxBytes) {
            List<Key> victims = eviction.selectVictims(EVICTION_BATCH);
            if (victims.isEmpty()) {
                break;
            }
            int removedCount = 0;
            for (Key victim : victims) {
                if (victim.equals(inserting)) {
                    continue;
                }
                CacheEntry removed = data.remove(victim);
                if (removed != null) {
                    bytes.addAndGet(-entryBytes(victim, removed.value()));
                    eviction.onRemove(victim);
                    evicted.add(victim);
                    removedCount++;
                }
            }
            if (removedCount == 0) {
                break;
            }
        }
        return evicted;
    }

    static long entryBytes(Key key, byte[] value) {
        return ENTRY_OVERHEAD + key.length() + value.length;
    }
}
