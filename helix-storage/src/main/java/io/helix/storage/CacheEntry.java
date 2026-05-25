package io.helix.storage;

/**
 * Immutable value holder with optional TTL metadata.
 */
public record CacheEntry(byte[] value, long expireAtEpochMs, long createdAtEpochMs, long lastAccessEpochMs) {

    public static final long NO_EXPIRE = 0;

    public static CacheEntry create(byte[] value, long expireAtEpochMs) {
        long now = System.currentTimeMillis();
        return new CacheEntry(value, expireAtEpochMs, now, now);
    }

    public boolean isExpired(long nowMs) {
        return expireAtEpochMs > 0 && nowMs >= expireAtEpochMs;
    }

    public boolean hasTtl() {
        return expireAtEpochMs > 0;
    }

    public CacheEntry withAccess(long nowMs) {
        return new CacheEntry(value, expireAtEpochMs, createdAtEpochMs, nowMs);
    }

    public CacheEntry withExpireAt(long expireAtEpochMs) {
        return new CacheEntry(value, expireAtEpochMs, createdAtEpochMs, lastAccessEpochMs);
    }
}
