package io.helix.storage;

import io.helix.core.HelixConfig;
import io.helix.core.Key;

import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Sharded in-memory store: one {@link ConcurrentHashMap} per segment (Phase 1 — no TTL/eviction).
 */
public final class SegmentedCacheEngine implements CacheEngine {

    private final Segment[] segments;
    private final int segmentMask;
    private final AtomicLong totalKeys = new AtomicLong();

    public SegmentedCacheEngine(HelixConfig config) {
        int count = config.segmentCountPowerOfTwo();
        this.segmentMask = count - 1;
        this.segments = new Segment[count];
        Arrays.setAll(segments, i -> new Segment());
    }

    @Override
    public void set(Key key, byte[] value) {
        Segment segment = segmentFor(key);
        boolean isNew = !segment.exists(key);
        segment.set(key, value);
        if (isNew) {
            totalKeys.incrementAndGet();
        }
    }

    @Override
    public Optional<byte[]> get(Key key) {
        return segmentFor(key).get(key);
    }

    @Override
    public boolean delete(Key key) {
        Segment segment = segmentFor(key);
        if (segment.delete(key)) {
            totalKeys.decrementAndGet();
            return true;
        }
        return false;
    }

    @Override
    public boolean exists(Key key) {
        return segmentFor(key).exists(key);
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

    private Segment segmentFor(Key key) {
        int index = (key.hashCode() & segmentMask);
        return segments[index];
    }
}
