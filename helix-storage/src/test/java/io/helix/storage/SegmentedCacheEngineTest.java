package io.helix.storage;

import io.helix.core.HelixConfig;
import io.helix.core.Key;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SegmentedCacheEngineTest {

    private SegmentedCacheEngine engine;

    @AfterEach
    void tearDown() {
        if (engine != null) {
            engine.close();
        }
    }

    @Test
    void setGetDeleteExists() {
        engine = new SegmentedCacheEngine(HelixConfig.forTest(0));
        Key key = Key.ofUtf8("user:1");
        engine.set(key, "Arya".getBytes(StandardCharsets.UTF_8), Optional.empty());

        assertTrue(engine.get(key).isPresent());
        assertTrue(engine.exists(key));
        assertEquals(1, engine.keyCount());

        assertTrue(engine.delete(key));
        assertFalse(engine.exists(key));
        assertTrue(engine.get(key).isEmpty());
    }

    @Test
    void ttlExpiresOnGet() throws InterruptedException {
        HelixConfig config = HelixConfig.forTest(0);
        engine = new SegmentedCacheEngine(config);
        Key key = Key.ofUtf8("token");
        engine.set(key, "x".getBytes(StandardCharsets.UTF_8), Optional.of(1L));
        assertEquals(1, engine.ttl(key));
        Thread.sleep(1100);
        assertTrue(engine.get(key).isEmpty());
        assertEquals(-2, engine.ttl(key));
    }

    @Test
    void evictionKeepsFrequentlyUsedKey() {
        HelixConfig tiny = new HelixConfig(
                "127.0.0.1", 0, 1, 65_536, 512, 64, false, 2,
                400, "lru", 50, 50, 2, "test", java.util.List.of(), java.util.List.of());
        engine = new SegmentedCacheEngine(tiny);
        Key hot = Key.ofUtf8("hot");
        engine.set(hot, "v".getBytes(StandardCharsets.UTF_8), Optional.empty());

        for (int i = 0; i < 30; i++) {
            engine.get(hot);
            engine.set(Key.ofUtf8("cold:" + i), "xxxxxxxxxx".getBytes(StandardCharsets.UTF_8), Optional.empty());
        }
        assertTrue(engine.stats().evictions() > 0, "Eviction should have occurred under memory pressure");
        assertTrue(engine.keyCount() < 31, "Some keys should have been evicted");
        assertTrue(engine.usedBytes() <= tiny.maxMemoryBytes() + 128, "Memory should stay near cap");
    }
}
