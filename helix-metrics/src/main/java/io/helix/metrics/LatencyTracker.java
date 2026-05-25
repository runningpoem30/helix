package io.helix.metrics;

import java.util.Arrays;

/**
 * Fixed-size ring buffer for approximate latency percentiles (Phase 4).
 */
final class LatencyTracker {

    private final long[] samples;
    private int index;
    private int count;

    LatencyTracker() {
        this.samples = new long[4096];
    }

    synchronized void record(long micros) {
        samples[index] = micros;
        index = (index + 1) % samples.length;
        if (count < samples.length) {
            count++;
        }
    }

    synchronized long percentile(int p) {
        if (count == 0) {
            return 0;
        }
        long[] copy = Arrays.copyOf(samples, count);
        Arrays.sort(copy);
        int idx = Math.min(count - 1, (int) Math.ceil(p / 100.0 * count) - 1);
        return copy[Math.max(0, idx)];
    }
}
