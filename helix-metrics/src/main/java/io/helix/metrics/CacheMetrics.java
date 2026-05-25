package io.helix.metrics;

import java.util.concurrent.atomic.LongAdder;

/**
 * Lock-free request and cache hit/miss counters (Phase 1 minimal metrics).
 */
public final class CacheMetrics {

    private final LongAdder requests = new LongAdder();
    private final LongAdder hits = new LongAdder();
    private final LongAdder misses = new LongAdder();

    public void recordRequest() {
        requests.increment();
    }

    public void recordHit() {
        hits.increment();
    }

    public void recordMiss() {
        misses.increment();
    }

    public long requests() {
        return requests.sum();
    }

    public long hits() {
        return hits.sum();
    }

    public long misses() {
        return misses.sum();
    }

    public double hitRatio() {
        long h = hits.sum();
        long m = misses.sum();
        long total = h + m;
        return total == 0 ? 0.0 : (double) h / total;
    }
}
