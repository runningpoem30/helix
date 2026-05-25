package io.helix.cluster;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConsistentHashRingTest {

    @Test
    void routesKeyToStableNode() {
        var ring = new ConsistentHashRing(List.of(
                NodeAddress.parse("127.0.0.1:6379"),
                NodeAddress.parse("127.0.0.1:6380")));
        NodeAddress first = ring.nodeForKey("user:42");
        NodeAddress second = ring.nodeForKey("user:42");
        assertEquals(first, second);
    }
}
