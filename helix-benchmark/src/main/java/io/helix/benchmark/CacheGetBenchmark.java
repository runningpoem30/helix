package io.helix.benchmark;

import io.helix.core.HelixConfig;
import io.helix.core.Key;
import io.helix.metrics.CacheMetrics;
import io.helix.storage.CacheService;
import io.helix.storage.SegmentedCacheEngine;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 3, time = 1)
@Fork(1)
@State(Scope.Benchmark)
public class CacheGetBenchmark {

    private CacheService service;

    @Setup
    public void setup() {
        HelixConfig config = HelixConfig.forTest(0);
        SegmentedCacheEngine engine = new SegmentedCacheEngine(config);
        service = new CacheService(engine, new CacheMetrics());
        service.execute(new io.helix.core.command.SetCommand(Key.ofUtf8("hot"), "value".getBytes(), Optional.empty()));
    }

    @Benchmark
    public void getHit() {
        service.execute(new io.helix.core.command.GetCommand(Key.ofUtf8("hot")));
    }
}
