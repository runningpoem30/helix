package io.helix.storage;

public record EngineStats(
        long keys,
        long usedBytes,
        long maxMemoryBytes,
        String evictionPolicy,
        long evictions,
        long expirations,
        String nodeId
) {}
