# Helix — System Architecture

Principal-level design for a production-oriented in-memory cache engine. This document is the source of truth for structure, data flow, and engineering tradeoffs.

---

## 1. Design goals

| Goal | Mechanism |
|------|-----------|
| Low latency | Netty NIO, minimal allocations on hot path, striped locks |
| High throughput | Event-loop I/O + bounded worker pool for storage ops |
| Predictable memory | Per-entry accounting, max-bytes cap, eviction |
| Correctness under concurrency | Striped `ConcurrentHashMap`, fine-grained RW locks for eviction metadata |
| Operability | TCP protocol, metrics, Docker, telnet-friendly demos |
| Interview signal | Explicit algorithms, complexity analysis, bottleneck docs |

**Non-goals (initial phases):** REST API, Spring in request path, full Redis compatibility, Kubernetes control plane.

---

## 2. Logical architecture

```
                    ┌─────────────────────────────────────────┐
                    │           Client Applications            │
                    │  (telnet, Java client, cache-aside demo) │
                    └────────────────────┬────────────────────┘
                                         │ TCP (text protocol)
                                         ▼
┌────────────────────────────────────────────────────────────────────────┐
│                         helix-server (JVM)                              │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │                    helix-network (Netty)                          │  │
│  │  Boss EventLoop → Accept                                          │  │
│  │  Worker EventLoop(s) → Decode → Dispatch → Encode → Flush         │  │
│  └───────────────────────────────┬──────────────────────────────────┘  │
│                                  │ Command objects                      │
│  ┌───────────────────────────────▼──────────────────────────────────┐  │
│  │              Command Parser / Validator (helix-core)              │  │
│  └───────────────────────────────┬──────────────────────────────────┘  │
│                                  │                                        │
│  ┌───────────────────────────────▼──────────────────────────────────┐  │
│  │           Cache Service Facade (orchestration, metrics)           │  │
│  └───────┬───────────────────────────────┬────────────────────────────┘  │
│          │                               │                                │
│          ▼                               ▼                                │
│  ┌───────────────┐              ┌────────────────┐                       │
│  │ helix-storage │◄────────────►│ helix-eviction │                       │
│  │  KV segments  │              │ LRU/LFU/FIFO   │                       │
│  │  TTL index    │              │ policy + heap  │                       │
│  └───────────────┘              └────────────────┘                       │
│          │                                                                │
│          ▼                                                                │
│  ┌───────────────┐     ┌─────────────────┐     (Phase 5)                 │
│  │ TTL Worker    │     │ helix-metrics   │     Cluster / replication    │
│  │ (scheduled)   │     │ hit ratio, etc. │                               │
│  └───────────────┘     └─────────────────┘                               │
└────────────────────────────────────────────────────────────────────────┘
```

### Module responsibilities

| Module | Responsibility |
|--------|----------------|
| **helix-core** | `Command`/`Response` types, protocol constants, `HelixConfig`, validation, error codes |
| **helix-network** | Netty bootstrap, pipeline, codecs, connection lifecycle, backpressure hooks |
| **helix-storage** | `CacheEngine`, segments, entry layout, TTL registry, memory accounting |
| **helix-eviction** | `EvictionPolicy` SPI, LRU/LFU/FIFO structures, eviction triggers |
| **helix-server** | `main()`, DI-by-hand wiring, graceful shutdown, config from env/args |
| **helix-metrics** | Atomic counters, latency recording, export interface (Phase 4) |
| **helix-benchmark** | JMH benchmarks + multi-connection load generator |
| **helix-examples** | Cache-aside pattern with simulated DB |
| **helix-cli** | Thin TCP client for demos |

---

## 3. Request lifecycle (end-to-end)

```
Client bytes on socket
    │
    ▼
[Netty Worker] ByteBuf accumulates until CRLF line(s) complete
    │
    ▼
[ProtocolDecoder] → ParsedCommand (immutable record)
    │
    ▼
[CommandHandler] submit to StorageExecutor OR run inline (config)
    │
    ▼
[CacheService]
    ├─ EXISTS? → segment lookup (read lock stripe)
    ├─ GET?    → lookup + TTL check + touch eviction metadata
    ├─ SET?    → memory check → maybe evict → write + TTL register
    └─ DELETE / EXPIRE / TTL → mutate storage + eviction + TTL index
    │
    ▼
[ResponseEncoder] → "+OK\r\n" / bulk string / integer / error
    │
    ▼
Flush to channel (same event loop or listener callback)
```

**Key principle:** Netty threads must not block on contended locks for long periods. Storage work can run on a dedicated `ExecutorService` with bounded queue; responses are written back on the channel's event loop via `channel.eventLoop().execute(...)`.

---

## 4. TCP protocol (summary)

Full spec: [PROTOCOL.md](PROTOCOL.md).

- **Transport:** TCP, one command per line (Phase 1), CRLF-terminated.
- **Encoding:** UTF-8 text, space-delimited tokens.
- **Port default:** 6379 (configurable; document if colliding with Redis locally).

Example session:

```
SET user:1 Arya
+OK

GET user:1
$4
Arya

SET token abc EX 5
+OK

TTL token
:5

GET token        # after 5s
$-1
```

Responses use a minimal RESP-inspired framing (simple types: `+` OK, `-` ERR, `$` bulk, `:` integer, `(nil)` as `$-1`) — familiar to reviewers without claiming Redis compatibility.

---

## 5. Storage engine (summary)

Full spec: [STORAGE.md](STORAGE.md).

### Entry model

```java
record CacheEntry(
    byte[] key,
    byte[] value,
    long expireAtEpochMs,  // 0 = no TTL
    long createdAt,
    long lastAccessAt
) {}
```

### Segmentation (concurrency)

- **256 segments** (configurable power-of-two): `segmentIndex = hash(key) & (segments - 1)`.
- Each segment: `ConcurrentHashMap<KeyWrapper, CacheEntry>` + **stripe-specific** `ReentrantReadWriteLock` for eviction structure updates that must be consistent with the map.
- **Why:** Reduces lock contention vs one global map; standard technique (similar spirit to `ConcurrentHashMap` bins).

### Memory accounting

- Track `usedBytes` atomically: key length + value length + overhead constant per entry.
- On `SET`, if `usedBytes + delta > maxBytes` → run eviction until under cap or reject (config: `evict` vs `error`).

---

## 6. TTL expiration (summary)

Full spec: [TTL.md](TTL.md).

**Two mechanisms (complementary):**

1. **Lazy expiration** — On `GET`/`EXISTS`, if `now > expireAt`, treat as miss, delete synchronously.
2. **Active expiration** — Background worker scans TTL index in buckets (time wheel / hierarchical timing wheel lite).

**TTL index structure (Phase 2):**

- `ConcurrentSkipListMap<expireAt, Set<key>>` per segment OR global time-bucketed queues.
- Worker wakes every `tickMs`, processes due bucket, removes keys.

**Tradeoffs:**

| Strategy | Pros | Cons |
|----------|------|------|
| Lazy only | Zero background CPU | Memory held until accessed |
| Periodic full scan | Simple | O(N) — unacceptable at scale |
| Time-bucketed active | O(keys due) per tick | Bookkeeping overhead |
| Per-key `DelayQueue` | Precise | High memory, contention on pq |

**Helix choice:** Lazy + bucketed active (default 10ms–250ms tick, configurable).

---

## 7. Eviction policies (summary)

Full spec: [EVICTION.md](EVICTION.md).

Pluggable `EvictionPolicy` interface:

```java
interface EvictionPolicy {
    void onAccess(Key key, CacheEntry entry);
    void onInsert(Key key, CacheEntry entry);
    void onRemove(Key key);
    List<Key> selectVictims(int count);
}
```

| Policy | Structure | get/put | Evict pick | Memory overhead |
|--------|-----------|---------|------------|-----------------|
| **LRU** | LinkedHashMap order or doubly-linked list + HashMap | O(1) | O(1) tail | O(N) pointers |
| **LFU** | HashMap + min-heap of freq buckets (approximate LFU optional) | O(1) amortized | O(log N) or O(1) approx | Higher |
| **FIFO** | Queue + HashMap index | O(1) | O(1) head | O(N) |

Eviction runs **under segment write lock** after memory check fails.

---

## 8. Concurrency strategy (summary)

Full spec: [CONCURRENCY.md](CONCURRENCY.md).

| Operation | Synchronization |
|-----------|-----------------|
| GET (hit) | Segment CHM read + optional eviction metadata read lock |
| SET | Segment write lock → CHM put → eviction onInsert → TTL register |
| DELETE | Write lock stripe |
| Eviction batch | Write lock; remove victims from CHM + policy |
| Metrics | `LongAdder`, `AtomicLong` — no locks on hot path |

**Read-write lock per segment** protects eviction structure + map consistency during eviction. `ConcurrentHashMap` alone is insufficient when eviction list and map must stay in sync.

**Avoid:** Global synchronized cache; blocking Netty IO thread on eviction.

---

## 9. Netty architecture (summary)

Full spec: [NETTY.md](NETTY.md).

```
ServerBootstrap
  .group(bossGroup, workerGroup)
  .channel(NioServerSocketChannel)
  .childHandler(new ChannelInitializer<>() {
      ch.pipeline()
        .addLast("idle", new IdleStateHandler(...))
        .addLast("decoder", new HelixCommandDecoder())
        .addLast("encoder", new HelixResponseEncoder())
        .addLast("handler", new HelixCommandHandler(cacheService, executor));
  });
```

- **Boss:** 1 thread, accept connections.
- **Workers:** `2 × cores` default (configurable).
- **Business executor:** Fixed pool `cores × 2` with bounded `ArrayBlockingQueue` for overload policy (drop / `-ERR busy`).

---

## 10. Cache-aside demonstration

Module: `helix-examples`.

```
Request for key K
    → GET K from Helix
    → if hit: return
    → else: SELECT from SimulatedDB (sleep 20–50ms)
    → SET K with TTL
    → return
```

Demonstrates **cache hit ratio**, **stampede** risk (mention in docs), optional **singleflight** as future improvement.

---

## 11. Metrics & benchmarking (Phase 4)

See [BENCHMARKING.md](BENCHMARKING.md).

**Metrics (in-process):**

- `requests_total`, `hits`, `misses`, `evictions`, `expirations`
- Latency: P50/P95/P99 via HDR Histogram or fixed ring buffer
- `used_bytes`, `keys_count`

**JMH scenarios:**

- Single-key hot read
- Zipfian key distribution
- 90/10 read/write
- TTL-heavy churn

---

## 12. Deployment topology

See [DEPLOYMENT.md](DEPLOYMENT.md).

**Phase 1–4:** Single node, Docker published port 6379.

**Phase 5:** N nodes, client-side consistent hashing (ketama-style), no coordinator in v1 cluster.

**GCP:** e2-standard-4 VM, Docker Compose, firewall tcp:6379, optional Prometheus sidecar later.

---

## 13. Scaling & bottleneck analysis

See [PERFORMANCE.md](PERFORMANCE.md).

**Vertical scaling first:** More RAM, more worker threads, segment count tuning.

**Horizontal (Phase 5):** Partition keys across nodes; no cross-node transactions; replication async.

**Expected bottlenecks (ordered):**

1. Lock contention on hot segments (mitigate: more segments, key hashing)
2. Eviction under write pressure (mitigate: approximate LRU, batch evictions)
3. TTL worker vs write path (mitigate: bucketed expiry, lazy delete)
4. GC pressure from `byte[]` churn (mitigate: pooled buffers for network only; document object reuse policy)
5. Single-threaded event loop overload (mitigate: offload to executor, multiple worker groups)

---

## 14. Implementation phases

See [ROADMAP.md](ROADMAP.md).

| Phase | Deliverable |
|-------|-------------|
| 0 | Docs + Maven scaffold ← **you are here** |
| 1 | TCP SET/GET/DELETE/EXISTS, basic storage |
| 2 | TTL + EXPIRE + background expiry + concurrency review |
| 3 | LRU/LFU/FIFO + max memory |
| 4 | Metrics + JMH + load tool |
| 5 | Multi-node consistent hash + replication sketch |

---

## 15. Security & production notes (awareness)

- No auth in Phase 1 — bind `127.0.0.1` by default; document `--bind 0.0.0.0` risk.
- Max command size limit in decoder (anti-DoS).
- Connection limits per IP (later).
- TLS termination via stunnel/sidecar (not in core).

---

## 16. Diagram: memory & ownership

```
┌──────────────────────────────────────┐
│           JVM Heap                    │
│  ┌────────────┐  ┌─────────────────┐ │
│  │ Segment[0] │  │ EvictionMeta[0] │ │
│  │   CHM      │◄─┤ LRU list        │ │
│  └────────────┘  └─────────────────┘ │
│         ...           ...             │
│  ┌────────────────────────────────┐ │
│  │ TTL bucket index (per time slot) │ │
│  └────────────────────────────────┘ │
│  Netty direct buffers (pool) — I/O only│
└──────────────────────────────────────┘
```

Helix optimizes for **throughput under many connections**, not minimum heap footprint. Footprint is a first-class metric via `used_bytes`.

---

*Next step: implement Phase 1 per [ROADMAP.md](ROADMAP.md).*
