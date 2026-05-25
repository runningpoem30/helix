package io.helix.storage;

import io.helix.core.Key;

import java.util.Optional;

/**
 * Thread-safe in-memory key-value store.
 */
public interface CacheEngine {

    void set(Key key, byte[] value);

    Optional<byte[]> get(Key key);

    boolean delete(Key key);

    boolean exists(Key key);

    long keyCount();

    long usedBytes();
}
