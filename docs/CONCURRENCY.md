# Helix Concurrency Strategy

Helix is a multi-threaded, multi-connection server. Correctness and throughput depend on deliberate lock granularity — not on sprinkling `synchronized` everywhere.

---

## 1. Workload assumptions

- **Read-heavy** (80–95% GET) typical for caches
- **Many client threads** → many Netty worker + storage pool threads hit storage concurrently
- **Hot keys** create segment hotspots
- **Eviction bursts** under memory pressure are write-heavy

---

## 2. Java concurrency building blocks

| Tool | Usage in Helix |
|------|----------------|
| `ConcurrentHashMap` | Per-segment key → entry map |
| `ReentrantReadWriteLock` | Per-segment eviction + coordinated remove |
| `AtomicLong` / `LongAdder` | Metrics, `usedBytes` |
| `ExecutorService` | Storage offload, TTL worker |
| `ScheduledExecutorService` | Expiration ticks, INFO sampling |
| `CompletableFuture` | Async command completion to Netty |
| `volatile` / `VarHandle` | Server state flags (running/stopping) |

**Not used on hot path (by default):** global `synchronized`, `Semaphore` per GET.

---

## 3. Lock granularity

### Level 1: Segment stripe (primary)

- **256 segments** → up to 256 independent write locks for unrelated keys.
- Keys mapping to different segments: **fully parallel** writes.

### Level 2: Read vs write

| Operation | Lock |
|-----------|------|
| GET (no eviction touch) | CHM only (Phase 1) |
| GET + LRU touch | `evictionLock.readLock()` for list move OR atomic LRU approximation |
| SET / DELETE / evict | `evictionLock.writeLock()` |

**LRU optimization:** Use **probabilistic LRU** — only promote every Nth access without lock — document tradeoff in interviews (Redis-like approximate LRU spirit).

### Level 3: Global structures

- **TTL time wheel:** single `ReentrantLock` per wheel OR shard wheels by `expireAt % W`
- **Metrics:** `LongAdder` — no lock

---

## 4. Happens-before and visibility

- Cache entries are **immutable after publish** except TTL field updated under write lock.
- On SET, new `CacheEntry` object published via `CHM.put` — readers see full constructed entry.
- Deletes: `remove` then eviction `onRemove` under same write lock.

---

## 5. Avoiding deadlocks

**Lock ordering rule:**

1. Never hold two segment write locks at once (eviction never cross-segment in one operation).
2. Global TTL lock acquired **before** segment lock? **Avoid** — instead register TTL after segment release using key copy + epoch.

**TTL registration pattern:**

```
under segment write lock:
  put entry
after release:
  ttlIndex.schedule(key, expireAt)  // concurrent skiplist insert
```

If TTL insert fails, lazy GET still corrects.

---

## 6. Netty + thread safety

- Each `ChannelHandlerContext` is invoked on its event loop — handler instance fields must be thread-safe or immutable.
- `CacheService` is **shared** → must be thread-safe (delegates to engine).
- Do not store per-connection state in service singleton.

---

## 7. Contention reduction checklist

- [ ] Increase `NUM_SEGMENTS` (512, 1024) if profiling shows stripe hotspots
- [ ] Offload storage to thread pool sized ≈ cores
- [ ] Approximate LRU to reduce write lock hold time
- [ ] Batch evictions (evict 16 keys per policy call when over limit)
- [ ] Pre-size `ConcurrentHashMap` per segment (`initialCapacity / segments`)

---

## 8. Comparison table

| Approach | Throughput | Complexity | Helix |
|----------|------------|------------|-------|
| Single global lock | Low | Low | ✗ |
| CHM only | High reads | Eviction bugs | partial |
| CHM + per-segment RW | Balanced | Medium | ✓ |
| Lock-free custom | Highest | Very high | future research |

---

## 9. Virtual threads (Java 21)

Optional Phase 4 experiment: virtual thread per request on storage executor.

- **Pros:** simpler blocking style
- **Cons:** pinning if synchronized blocks; measure before adopting

Default remains **platform thread pool** for predictable behavior in demos.

---

## 10. Interview narrative

> "We stripe the keyspace into 256 segments, each with a ConcurrentHashMap for lock-free reads and a read-write lock guarding eviction metadata consistency. TTL registration is mostly lock-free via a concurrent time index. Netty never blocks on eviction — work runs on a bounded executor and responses hop back to the channel event loop."

---

*Cross-reference: [STORAGE.md](STORAGE.md), [NETTY.md](NETTY.md).*
