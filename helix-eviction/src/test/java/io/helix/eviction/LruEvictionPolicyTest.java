package io.helix.eviction;

import io.helix.core.Key;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LruEvictionPolicyTest {

    @Test
    void evictsLeastRecentlyUsed() {
        LruEvictionPolicy policy = new LruEvictionPolicy();
        Key a = Key.ofUtf8("a");
        Key b = Key.ofUtf8("b");
        Key c = Key.ofUtf8("c");
        policy.onInsert(a);
        policy.onInsert(b);
        policy.onInsert(c);
        policy.onAccess(a);
        var victims = policy.selectVictims(1);
        assertEquals(1, victims.size());
        assertEquals(b, victims.getFirst());
        assertTrue(policy.size() >= 2);
    }
}
