package io.helix.storage;

import io.helix.core.command.Command;
import io.helix.core.command.DeleteCommand;
import io.helix.core.command.ExistsCommand;
import io.helix.core.command.GetCommand;
import io.helix.core.command.PingCommand;
import io.helix.core.command.QuitCommand;
import io.helix.core.command.SetCommand;
import io.helix.core.response.BulkResponse;
import io.helix.core.response.CloseConnectionResponse;
import io.helix.core.response.IntegerResponse;
import io.helix.core.response.Response;
import io.helix.core.response.SimpleResponse;
import io.helix.metrics.CacheMetrics;

import java.util.Optional;

/**
 * Executes parsed commands against the storage engine and records metrics.
 */
public final class CacheService {

    private final CacheEngine engine;
    private final CacheMetrics metrics;

    public CacheService(CacheEngine engine, CacheMetrics metrics) {
        this.engine = engine;
        this.metrics = metrics;
    }

    public Response execute(Command command) {
        metrics.recordRequest();
        return switch (command) {
            case PingCommand ping -> SimpleResponse.PONG;
            case QuitCommand quit -> new CloseConnectionResponse();
            case SetCommand set -> executeSet(set);
            case GetCommand get -> executeGet(get);
            case DeleteCommand del -> executeDelete(del);
            case ExistsCommand exists -> executeExists(exists);
        };
    }

    private Response executeSet(SetCommand set) {
        engine.set(set.key(), set.value());
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
        return new IntegerResponse(removed);
    }

    private Response executeExists(ExistsCommand exists) {
        return new IntegerResponse(engine.exists(exists.key()) ? 1 : 0);
    }
}
