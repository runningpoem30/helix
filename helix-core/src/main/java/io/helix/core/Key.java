package io.helix.core;

import java.util.Arrays;
import java.util.Objects;

/**
 * Immutable cache key backed by raw bytes (wire-format faithful).
 */
public final class Key {

    private final byte[] bytes;
    private final int hash;

    public Key(byte[] bytes) {
        this.bytes = Objects.requireNonNull(bytes, "bytes");
        this.hash = Arrays.hashCode(bytes);
    }

    public static Key ofUtf8(String s) {
        return new Key(s.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    public byte[] bytes() {
        return bytes;
    }

    public int length() {
        return bytes.length;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof Key other)) {
            return false;
        }
        return Arrays.equals(bytes, other.bytes);
    }

    @Override
    public int hashCode() {
        return hash;
    }

    @Override
    public String toString() {
        return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
    }
}
