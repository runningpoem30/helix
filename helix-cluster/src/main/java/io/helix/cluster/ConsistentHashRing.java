package io.helix.cluster;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Ketama-style consistent hash ring with virtual nodes.
 */
public final class ConsistentHashRing {

    private static final int VIRTUAL_NODES = 150;

    private final SortedMap<Long, NodeAddress> ring = new TreeMap<>();

    public ConsistentHashRing(Collection<NodeAddress> nodes) {
        for (NodeAddress node : nodes) {
            for (int i = 0; i < VIRTUAL_NODES; i++) {
                long hash = hash(node + "#" + i);
                ring.put(hash, node);
            }
        }
    }

    public NodeAddress nodeForKey(String key) {
        if (ring.isEmpty()) {
            throw new IllegalStateException("Empty hash ring");
        }
        long hash = hash(key);
        SortedMap<Long, NodeAddress> tail = ring.tailMap(hash);
        long slot = tail.isEmpty() ? ring.firstKey() : tail.firstKey();
        return ring.get(slot);
    }

    private static long hash(String input) {
        byte[] data = input.getBytes(StandardCharsets.UTF_8);
        long h = 0;
        for (byte b : data) {
            h = 31 * h + (b & 0xff);
        }
        return h & 0xffffffffL;
    }
}
