package io.helix.cluster;

import io.helix.core.Key;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Client-side consistent-hash router across Helix nodes.
 */
public final class ClusterClient implements AutoCloseable {

    private final ConsistentHashRing ring;
    private final Map<NodeAddress, HelixTcpClient> clients = new HashMap<>();

    public ClusterClient(List<String> peerHostPorts) {
        List<NodeAddress> nodes = peerHostPorts.stream().map(NodeAddress::parse).toList();
        this.ring = new ConsistentHashRing(nodes);
        for (NodeAddress node : nodes) {
            clients.put(node, new HelixTcpClient(node.host(), node.port()));
        }
    }

    public void connectAll() throws Exception {
        for (HelixTcpClient client : clients.values()) {
            client.connect();
        }
    }

    public List<String> set(String key, String value, Long ttlSeconds) throws Exception {
        String cmd = ttlSeconds == null
                ? "SET " + key + " " + value
                : "SET " + key + " " + value + " EX " + ttlSeconds;
        return route(key).send(cmd);
    }

    public List<String> get(String key) throws Exception {
        return route(key).send("GET " + key);
    }

    public List<String> info() throws Exception {
        return clients.values().iterator().next().send("INFO");
    }

    private HelixTcpClient route(String key) {
        NodeAddress node = ring.nodeForKey(key);
        return clients.get(node);
    }

    @Override
    public void close() throws Exception {
        for (HelixTcpClient client : clients.values()) {
            client.close();
        }
    }
}
