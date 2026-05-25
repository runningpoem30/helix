package io.helix.storage;

import io.helix.core.HelixConfig;
import io.helix.core.Key;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SegmentedCacheEngineTest {

    private final CacheEngine engine = new SegmentedCacheEngine(HelixConfig.forTest(0));

    @Test
    void setGetDeleteExists() {
        Key key = Key.ofUtf8("user:1");
        engine.set(key, "Arya".getBytes(StandardCharsets.UTF_8));

        Optional<byte[]> value = engine.get(key);
        assertTrue(value.isPresent());
        assertEquals("Arya", new String(value.get(), StandardCharsets.UTF_8));

        assertTrue(engine.exists(key));
        assertEquals(1, engine.keyCount());

        assertTrue(engine.delete(key));
        assertFalse(engine.exists(key));
        assertTrue(engine.get(key).isEmpty());
        assertEquals(0, engine.keyCount());
    }

    @Test
    void overwriteKeepsKeyCount() {
        Key key = Key.ofUtf8("k");
        engine.set(key, "a".getBytes(StandardCharsets.UTF_8));
        engine.set(key, "bb".getBytes(StandardCharsets.UTF_8));
        assertEquals(1, engine.keyCount());
        assertEquals("bb", new String(engine.get(key).orElseThrow(), StandardCharsets.UTF_8));
    }
}
