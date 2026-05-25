package io.helix.storage;

import io.helix.core.Key;

import java.util.ArrayList;
import java.util.List;
import java.util.NavigableMap;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 * Maps expiration timestamps to keys for active expiration sweeps.
 */
final class TtlIndex {

    private final ConcurrentHashMap<Key, Long> keyToExpire = new ConcurrentHashMap<>();
    private final NavigableMap<Long, Set<Key>> expireToKeys = new ConcurrentSkipListMap<>();

    void schedule(Key key, long expireAtEpochMs) {
        if (expireAtEpochMs <= 0) {
            unschedule(key);
            return;
        }
        Long previous = keyToExpire.put(key, expireAtEpochMs);
        if (previous != null) {
            removeFromBucket(previous, key);
        }
        expireToKeys.computeIfAbsent(expireAtEpochMs, k -> ConcurrentHashMap.newKeySet()).add(key);
    }

    void unschedule(Key key) {
        Long expireAt = keyToExpire.remove(key);
        if (expireAt != null) {
            removeFromBucket(expireAt, key);
        }
    }

    List<Key> collectExpired(long nowMs, int maxKeys) {
        List<Key> expired = new ArrayList<>(maxKeys);
        var head = expireToKeys.headMap(nowMs, true);
        for (var entry : head.entrySet()) {
            long expireAt = entry.getKey();
            Set<Key> keys = entry.getValue();
            for (Key key : keys) {
                Long mapped = keyToExpire.get(key);
                if (mapped != null && mapped == expireAt && expired.size() < maxKeys) {
                    expired.add(key);
                }
            }
            expireToKeys.remove(expireAt);
        }
        for (Key key : expired) {
            keyToExpire.remove(key);
        }
        return expired;
    }

    private void removeFromBucket(long expireAt, Key key) {
        Set<Key> bucket = expireToKeys.get(expireAt);
        if (bucket != null) {
            bucket.remove(key);
            if (bucket.isEmpty()) {
                expireToKeys.remove(expireAt, bucket);
            }
        }
    }
}
