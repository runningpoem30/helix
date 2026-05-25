package io.helix.storage;

import io.helix.core.Key;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

final class ExpirationWorker implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(ExpirationWorker.class);
    private static final int MAX_EXPIRE_PER_TICK = 256;

    private final SegmentedCacheEngine engine;
    private final TtlIndex ttlIndex;
    private final ScheduledExecutorService scheduler;
    private final int tickMs;
    private final AtomicLong expirations = new AtomicLong();
    private ScheduledFuture<?> task;

    ExpirationWorker(
            SegmentedCacheEngine engine,
            TtlIndex ttlIndex,
            ScheduledExecutorService scheduler,
            int tickMs) {
        this.engine = engine;
        this.ttlIndex = ttlIndex;
        this.scheduler = scheduler;
        this.tickMs = tickMs;
    }

    void start() {
        task = scheduler.scheduleAtFixedRate(this::tick, tickMs, tickMs, TimeUnit.MILLISECONDS);
    }

    private void tick() {
        try {
            long now = System.currentTimeMillis();
            List<Key> expired = ttlIndex.collectExpired(now, MAX_EXPIRE_PER_TICK);
            for (Key key : expired) {
                if (engine.expireKey(key)) {
                    expirations.incrementAndGet();
                }
            }
        } catch (Exception e) {
            log.warn("Expiration tick failed: {}", e.getMessage());
        }
    }

    long expirations() {
        return expirations.get();
    }

    @Override
    public void close() {
        if (task != null) {
            task.cancel(false);
        }
    }
}
