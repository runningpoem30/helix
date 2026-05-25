# Helix Performance & Scaling

Analysis of bottlenecks, scaling paths, and optimization order — measurement-driven, not premature.

---

## 1. Latency budget (TCP GET hit)

Approximate breakdown on loopback:

| Stage | Typical share |
|-------|----------------|
| Kernel + loopback | 20–30% |
| Netty read/decode | 15–25% |
| Cache lookup | 10–20% |
| Encode + flush | 15–25% |
| JVM/GC jitter | variable |

**First optimization target:** parsing and allocations (reuse `ByteBuf`, avoid `String` on hot path).

---

## 2. Throughput levers

| Lever | Effect |
|-------|--------|
| More Netty worker threads | Better multi-core accept/read until saturation |
| Segment count ↑ | Reduces lock collision |
| Executor offload | Prevents event loop blocking |
| `TCP_NODELAY` | Lower latency small packets |
| Pipelining | Higher ops/sec per connection |
| Batch eviction | Fewer lock acquisitions per SET burst |

---

## 3. Memory efficiency

| Technique | Impact |
|-----------|--------|
| `byte[]` for values | Simple, GC pressure |
| Value size cap | Prevents OOM |
| Accurate `usedBytes` | Correct eviction trigger |
| Active TTL | Reclaim cold expired keys |
| Entry overhead constant tuning | Calibrate with JOL (Java Object Layout) |

**Avoid:** Storing duplicate `String` of key in policy and map — policy references `Key` objects shared with map.

---

## 4. Lock contention analysis

**Symptom:** CPU high, throughput flat as clients increase.

**Diagnose:**

- JFR: `java.monitor.blocked`
- Metric: segment write lock wait time (micrometer later)

**Mitigations (ordered):**

1. Increase segments to 1024
2. Approximate LRU
3. Shrink eviction batch frequency
4. Separate hot key replication (advanced)

---

## 5. GC as bottleneck

**Symptom:** P99 spikes, `Allocation Rate` high in JFR.

**Mitigations:**

- Pool network buffers (Netty default)
- Reduce `new String(line)` in decoder — parse from `ByteBuf` directly
- Reuse command objects (object pool) — only if JMH proves benefit

---

## 6. TTL worker interference

**Symptom:** Periodic latency spikes every `tickMs`.

**Mitigations:**

- Limit keys processed per tick (`maxExpiredPerTick`)
- Spread buckets
- Lower tick frequency when idle (adaptive scheduler)

---

## 7. Vertical vs horizontal scaling

### Vertical (Phase 1–4)

- Bigger heap → more keys
- More cores → more Netty workers + storage threads
- **Ceiling:** single JVM heap, single NIC

### Horizontal (Phase 5)

```
         Client
            │
            ▼
    ┌───────────────┐
    │ Client router  │  consistent hash(key) → node
    └───────┬───────┘
      ┌─────┼─────┐
      ▼     ▼     ▼
   Node A Node B Node C
```

- **No cross-slot multi-key transactions**
- **Replication:** async, read-from-replica optional
- **Rebalancing:** add node → only fraction of keys move (virtual nodes)

---

## 8. Consistent hashing (Phase 5 sketch)

- 150 virtual nodes per physical node (typical)
- MurmurHash(key) → ring position
- Walk clockwise for primary; next for replica

**Interview point:** Explain hotspot mitigation with vnodes vs naive mod-N.

---

## 9. Bottleneck decision tree

```
Throughput low?
├─ CPU < 50% → network/lock? check blocked threads
├─ CPU 100% → profile hot methods (async-profiler)
│   ├─ CHM get dominates → already fast; check decode
│   └─ eviction dominates → batch evict, approximate LRU
└─ GC logs → allocation profiling
```

---

## 10. What not to optimize early

- Custom off-heap storage (Chronicle / unsafe) — complexity
- Kernel bypass — out of scope
- SIMD — irrelevant for hash map

Ship **correct + measured** before exotic optimizations.

---

*Revisit after Phase 4 benchmarks.*
