package io.helix.storage;

import io.helix.core.command.Command;
import io.helix.core.command.DeleteCommand;
import io.helix.core.command.ExistsCommand;
import io.helix.core.command.ExpireCommand;
import io.helix.core.command.GetCommand;
import io.helix.core.command.InfoCommand;
import io.helix.core.command.PingCommand;
import io.helix.core.command.QuitCommand;
import io.helix.core.command.SetCommand;
import io.helix.core.command.TtlCommand;
import io.helix.core.response.BulkResponse;
import io.helix.core.response.BulkStringResponse;
import io.helix.core.response.CloseConnectionResponse;
import io.helix.core.response.IntegerResponse;
import io.helix.core.response.Response;
import io.helix.core.response.SimpleResponse;
import io.helix.metrics.CacheMetrics;
import io.helix.cluster.ReplicationClient;

import java.util.Optional;

/**
 * Executes parsed commands against the storage engine and records metrics.
 */
public final class CacheService {

    private final CacheEngine engine;
    private final CacheMetrics metrics;
    private final Optional<ReplicationClient> replication;

    public CacheService(CacheEngine engine, CacheMetrics metrics) {
        this(engine, metrics, Optional.empty());
    }

    public CacheService(CacheEngine engine, CacheMetrics metrics, Optional<ReplicationClient> replication) {
        this.engine = engine;
        this.metrics = metrics;
        this.replication = replication;
    }

    public Response execute(Command command) {
        long start = System.nanoTime();
        metrics.recordRequest();
        try {
            return dispatch(command);
        } finally {
            metrics.recordLatencyMicros((System.nanoTime() - start) / 1000);
        }
    }

    private Response dispatch(Command command) {
        return switch (command) {
            case PingCommand ping -> SimpleResponse.PONG;
            case QuitCommand quit -> new CloseConnectionResponse();
            case SetCommand set -> executeSet(set);
            case GetCommand get -> executeGet(get);
            case DeleteCommand del -> executeDelete(del);
            case ExistsCommand exists -> executeExists(exists);
            case ExpireCommand expire -> executeExpire(expire);
            case TtlCommand ttl -> new IntegerResponse(engine.ttl(ttl.key()));
            case InfoCommand info -> executeInfo();
        };
    }

    private Response executeSet(SetCommand set) {
        engine.set(set.key(), set.value(), set.ttlSeconds());
        replication.ifPresent(r -> r.replicateSet(set.key(), set.value(), set.ttlSeconds()));
        return SimpleResponse.OK;
    }

    private Response executeGet(GetCommand get) {
        Optional<byte[]> value = engine.get(get.key());
        if (value.isPresent()) {
            metrics.recordHit();
            return BulkResponse.of(value.get());
        }
        metrics.recordMiss();
        return BulkResponse.NIL;
    }

    private Response executeDelete(DeleteCommand del) {
        int removed = engine.delete(del.key()) ? 1 : 0;
        if (removed == 1) {
            replication.ifPresent(r -> r.replicateDelete(del.key()));
        }
        return new IntegerResponse(removed);
    }

    private Response executeExists(ExistsCommand exists) {
        return new IntegerResponse(engine.exists(exists.key()) ? 1 : 0);
    }

    private Response executeExpire(ExpireCommand expire) {
        boolean ok = engine.expire(expire.key(), expire.ttlSeconds());
        if (ok) {
            replication.ifPresent(r -> r.replicateExpire(expire.key(), expire.ttlSeconds()));
        }
        return new IntegerResponse(ok ? 1 : 0);
    }

    private Response executeInfo() {
        EngineStats stats = engine.stats();
        String body = """
                # Server
                version:%s
                node_id:%s
                # Stats
                keys:%d
                used_memory_bytes:%d
                maxmemory_bytes:%d
                eviction_policy:%s
                evictions:%d
                expirations:%d
                # Metrics
                requests:%d
                hits:%d
                misses:%d
                hit_ratio:%.4f
                latency_p50_us:%d
                latency_p99_us:%d
                """.formatted(
                io.helix.core.HelixConfig.VERSION,
                stats.nodeId(),
                stats.keys(),
                stats.usedBytes(),
                stats.maxMemoryBytes(),
                stats.evictionPolicy(),
                stats.evictions(),
                stats.expirations(),
                metrics.requests(),
                metrics.hits(),
                metrics.misses(),
                metrics.hitRatio(),
                metrics.latencyP50Micros(),
                metrics.latencyP99Micros());
        return BulkStringResponse.of(body);
    }
}
