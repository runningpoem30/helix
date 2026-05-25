package io.helix.storage;

import io.helix.core.HelixConfig;
import io.helix.core.Key;
import io.helix.eviction.EvictionPolicy;
import io.helix.eviction.EvictionPolicyFactory;

import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Sharded in-memory store with per-segment eviction, TTL index, and background expiration.
 */
public final class SegmentedCacheEngine implements CacheEngine {

    private final HelixConfig config;
    private final Segment[] segments;
    private final int segmentMask;
    private final TtlIndex ttlIndex = new TtlIndex();
    private final AtomicLong totalKeys = new AtomicLong();
    private final AtomicLong evictions = new AtomicLong();
    private final ScheduledExecutorService scheduler;
    private final ExpirationWorker expirationWorker;

    public SegmentedCacheEngine(HelixConfig config) {
        this.config = config;
        int count = config.segmentCountPowerOfTwo();
        this.segmentMask = count - 1;
        long maxPerSegment = config.maxMemoryBytes() / count;
        EvictionPolicy template = EvictionPolicyFactory.create(config.evictionPolicy());
        this.segments = new Segment[count];
        Arrays.setAll(segments, i -> new Segment(clonePolicy(template), maxPerSegment));
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "helix-expiration");
            t.setDaemon(true);
            return t;
        });
        this.expirationWorker = new ExpirationWorker(this, ttlIndex, scheduler, config.ttlTickMs());
        this.expirationWorker.start();
    }

    private static EvictionPolicy clonePolicy(EvictionPolicy template) {
        return EvictionPolicyFactory.create(template.name());
    }

    @Override
    public void set(Key key, byte[] value, Optional<Long> ttlSeconds) {
        long expireAt = ttlSeconds.map(s -> System.currentTimeMillis() + s * 1000L).orElse(CacheEntry.NO_EXPIRE);
        CacheEntry entry = CacheEntry.create(value, expireAt);
        Segment segment = segmentFor(key);
        Segment.SetResult result = segment.set(key, entry);
        if (result.isNew()) {
            totalKeys.incrementAndGet();
        }
        onEvicted(result.evictedKeys());
        if (expireAt > 0) {
            ttlIndex.schedule(key, expireAt);
        } else {
            ttlIndex.unschedule(key);
        }
    }

    @Override
    public Optional<byte[]> get(Key key) {
        CacheEntry entry = loadValidEntry(key);
        if (entry == null) {
            return Optional.empty();
        }
        segmentFor(key).touch(key);
        return Optional.of(entry.value());
    }

    @Override
    public boolean delete(Key key) {
        ttlIndex.unschedule(key);
        Segment segment = segmentFor(key);
        if (segment.delete(key)) {
            totalKeys.decrementAndGet();
            return true;
        }
        return false;
    }

    @Override
    public boolean exists(Key key) {
        return loadValidEntry(key) != null;
    }

    @Override
    public boolean expire(Key key, long ttlSeconds) {
        if (ttlSeconds <= 0) {
            return false;
        }
        Segment segment = segmentFor(key);
        Optional<CacheEntry> current = segment.getEntry(key);
        if (current.isEmpty()) {
            return false;
        }
        long expireAt = System.currentTimeMillis() + ttlSeconds * 1000L;
        CacheEntry updated = current.get().withExpireAt(expireAt);
        segment.updateEntry(key, updated);
        ttlIndex.schedule(key, expireAt);
        return true;
    }

    @Override
    public long ttl(Key key) {
        CacheEntry entry = loadValidEntry(key);
        if (entry == null) {
            return -2;
        }
        if (!entry.hasTtl()) {
            return -1;
        }
        long remainingMs = entry.expireAtEpochMs() - System.currentTimeMillis();
        if (remainingMs <= 0) {
            return -2;
        }
        return (remainingMs + 999) / 1000;
    }

    @Override
    public long keyCount() {
        return totalKeys.get();
    }

    @Override
    public long usedBytes() {
        long sum = 0;
        for (Segment segment : segments) {
            sum += segment.usedBytes();
        }
        return sum;
    }

    @Override
    public EngineStats stats() {
        return new EngineStats(
                keyCount(),
                usedBytes(),
                config.maxMemoryBytes(),
                config.evictionPolicy(),
                evictions.get(),
                expirationWorker.expirations(),
                config.nodeId());
    }

    @Override
    public void close() {
        expirationWorker.close();
        scheduler.shutdownNow();
    }

    boolean expireKey(Key key) {
        Segment segment = segmentFor(key);
        CacheEntry removed = segment.removeForExpiration(key);
        if (removed != null) {
            ttlIndex.unschedule(key);
            totalKeys.decrementAndGet();
            return true;
        }
        return false;
    }

    private CacheEntry loadValidEntry(Key key) {
        Segment segment = segmentFor(key);
        Optional<CacheEntry> entry = segment.getEntry(key);
        if (entry.isEmpty()) {
            return null;
        }
        long now = System.currentTimeMillis();
        if (entry.get().isExpired(now)) {
            expireKey(key);
            return null;
        }
        return entry.get();
    }

    private void onEvicted(java.util.List<Key> keys) {
        for (Key key : keys) {
            ttlIndex.unschedule(key);
            totalKeys.decrementAndGet();
            evictions.incrementAndGet();
        }
    }

    private Segment segmentFor(Key key) {
        return segments[key.hashCode() & segmentMask];
    }
}
