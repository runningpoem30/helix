# Helix Benchmarking Strategy

Benchmarks prove claims. Helix uses layered benchmarking: micro (JMH), integration (multi-client TCP), and observability (runtime metrics).

---

## 1. Goals

| Question | Tool |
|----------|------|
| How fast is `get()` in isolation? | JMH |
| How many TCP ops/sec at 100 clients? | Load driver |
| What is P99 latency under mixed load? | Load driver + histogram |
| When does eviction hurt throughput? | Scenario with maxmemory = 10% of data |

**Rule:** Publish numbers only with hardware spec, JDK version, and commit hash.

---

## 2. JMH microbenchmarks (`helix-benchmark`)

### Setup

```xml
<!-- org.openjdk.jmh jmh-core + jmh-generator-annprocess -->
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
```

### Benchmarks

| Benchmark | Description |
|-----------|-------------|
| `SegmentedCacheGetHit` | GET existing key, no network |
| `SegmentedCacheGetMiss` | GET missing key |
| `SegmentedCacheSet` | PUT new keys cycling |
| `SegmentedCacheSetWithEviction` | Fixed keyspace, maxmemory triggers LRU |
| `LruTouch` | Policy `onAccess` only |
| `TtlLazyExpire` | GET expired entry |

### Parameters (`@Param`)

- `segments`: 64, 256, 1024
- `policy`: lru, lfu, fifo
- `keyDistribution`: uniform, zipf (synthetic)

---

## 3. TCP load driver

`helix-benchmark` CLI:

```bash
java -jar helix-benchmark.jar \
  --host localhost --port 6379 \
  --clients 50 --duration 60s \
  --ratio 90:10 \
  --keyspace 100000
```

**Metrics emitted:**

- ops/sec (aggregate and per-client)
- latency P50/P95/P99 (HdrHistogram)
- errors/timeouts

### Scenarios

| Scenario | Config | Insight |
|----------|--------|---------|
| Read-heavy | 95% GET | Baseline cache throughput |
| Mixed RW | 50/50 | Lock contention |
| Expiration-heavy | SET EX 30, no GET | TTL worker + memory |
| Hot key | 80% traffic on 10 keys | Segment hotspot |
| Concurrent clients | clients=200 | Netty + executor saturation |

---

## 4. Runtime metrics (Phase 4)

Exposed via `INFO` command and optional HTTP (Spring admin later):

```
# Stats
requests: 12849021
hits: 11200444
misses: 1648577
hit_ratio: 0.872
evictions: 44291
expirations: 12004
used_memory_bytes: 536870912
keys: 1048576
uptime_seconds: 86400

# Latency (microseconds)
latency_p50: 42
latency_p99: 310
```

**Collection:**

- `LongAdder` for counters
- Latency: record `(nanoTime - start)` in `ThreadLocal` ring or HdrHistogram per interval

---

## 5. Memory measurement

- **JVM:** `-XX:NativeMemoryTracking=summary` for deep dives
- **Application:** `used_bytes` from engine
- **Process:** `/usr/bin/time -v` RSS during load test

Plot **keys vs RSS** during fill-to-eviction test.

---

## 6. Baseline comparison (honest)

Compare against:

- **Local Redis** (reference, not competitor narrative)
- **Caffeine** in-heap (shows network cost of Helix)

Frame as: "Helix adds TCP + custom policy overhead; in-process GET target is within 2× of Caffeine before optimization."

---

## 7. CI benchmarking

- Nightly JMH on `main` with regression threshold 5%
- Store results JSON in `benchmarks/results/`
- PR comment optional (Phase 4+)

---

## 8. Reporting template (for README / blog)

```markdown
## Results (M2 MacBook, JDK 21.0.2, Helix 0.3.0)
- TCP GET hit: 180k ops/s, P99 0.8ms (50 clients, loopback)
- In-process GET hit: 4.2M ops/s (JMH, 256 segments)
- Eviction storm: 40k SET/s → 12k SET/s at maxmemory
```

---

*Implement in Phase 4 after correctness tests pass.*
