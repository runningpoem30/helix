package io.helix.storage;

import io.helix.core.Key;

import java.util.Optional;

/**
 * Thread-safe in-memory key-value store with TTL and eviction.
 */
public interface CacheEngine {

    void set(Key key, byte[] value, Optional<Long> ttlSeconds);

    Optional<byte[]> get(Key key);

    boolean delete(Key key);

    boolean exists(Key key);

    boolean expire(Key key, long ttlSeconds);

    /** -2 missing, -1 no TTL, else seconds remaining */
    long ttl(Key key);

    long keyCount();

    long usedBytes();

    EngineStats stats();

    void close();
}
