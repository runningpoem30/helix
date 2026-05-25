package io.helix.cluster;

import io.helix.core.Key;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Async fire-and-forget replication to replica nodes (Phase 5 prototype).
 */
public final class ReplicationClient implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(ReplicationClient.class);

    private final List<HelixTcpClient> replicas;
    private final ExecutorService executor;

    public ReplicationClient(List<String> replicaHostPorts) {
        this.replicas = replicaHostPorts.stream()
                .map(NodeAddress::parse)
                .map(n -> new HelixTcpClient(n.host(), n.port()))
                .toList();
        this.executor = Executors.newFixedThreadPool(Math.max(1, replicas.size()), r -> {
            Thread t = new Thread(r, "helix-replication");
            t.setDaemon(true);
            return t;
        });
        for (HelixTcpClient replica : replicas) {
            try {
                replica.connect();
            } catch (Exception e) {
                log.warn("Replica {} unavailable: {}", replica, e.getMessage());
            }
        }
    }

    public void replicateSet(Key key, byte[] value, Optional<Long> ttlSeconds) {
        String valueStr = new String(value, StandardCharsets.UTF_8);
        String cmd = ttlSeconds.map(t -> "SET " + key + " " + valueStr + " EX " + t)
                .orElse("SET " + key + " " + valueStr);
        replicate(cmd);
    }

    public void replicateDelete(Key key) {
        replicate("DELETE " + key);
    }

    public void replicateExpire(Key key, long ttlSeconds) {
        replicate("EXPIRE " + key + " " + ttlSeconds);
    }

    private void replicate(String command) {
        for (HelixTcpClient replica : replicas) {
            executor.execute(() -> {
                try {
                    replica.send(command);
                } catch (Exception e) {
                    log.debug("Replication failed: {}", e.getMessage());
                }
            });
        }
    }

    @Override
    public void close() {
        executor.shutdownNow();
        for (HelixTcpClient replica : replicas) {
            try {
                replica.close();
            } catch (Exception ignored) {
                // ignore
            }
        }
    }
}
