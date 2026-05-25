package io.helix.eviction;

public final class EvictionPolicyFactory {

    private EvictionPolicyFactory() {}

    public static EvictionPolicy create(String name) {
        return switch (name.toLowerCase()) {
            case "lfu" -> new LfuEvictionPolicy();
            case "fifo" -> new FifoEvictionPolicy();
            default -> new LruEvictionPolicy();
        };
    }
}
