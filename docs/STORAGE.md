# Helix Storage Engine

The storage engine is the source of truth for key-value data, TTL metadata, and memory accounting. It must be correct under concurrency and fast on the read path.

---

## 1. Responsibilities

- Put / get / delete / exists
- Attach and query TTL
- Report memory usage and key count
- Invoke eviction policy when over `maxmemory`
- Expose segment-level statistics for metrics

---

## 2. Core interfaces

```java
public interface CacheEngine {
    PutResult set(Key key, byte[] value, Optional<Duration> ttl);
    Optional<byte[]> get(Key key);
    boolean delete(Key key);
    boolean exists(Key key);
    boolean expire(Key key, Duration ttl);
    Optional<Duration> ttl(Key key);
    EngineStats stats();
}

public record EngineStats(long keys, long usedBytes, long hits, long misses) {}
```

`CacheEngine` implementation: `SegmentedCacheEngine` in `helix-storage`.

---

## 3. Key representation

```java
public final class Key {
    private final byte[] bytes;
    private final int hash; // precomputed murmur3 or Arrays.hashCode

    // equality on byte[] contents
}
```

**Why `byte[]` not `String`?** Avoid charset ambiguity; align with on-the-wire bytes; optional zero-copy from decoder in later phases.

**Key limits:** default max 512 bytes (config).

---

## 4. Segmented design

```
hash(key) & (NUM_SEGMENTS - 1)  →  segmentId

Segment {
    ConcurrentHashMap<Key, CacheEntry> data;
    ReentrantReadWriteLock evictionLock;
    EvictionPolicy eviction;  // per-segment or shared policy instance
    AtomicLong segmentBytes;
}
```

### Why segments?

- **ConcurrentHashMap** scales reads well but eviction structures need atomic batch updates with the map.
- Striping **256 ways** spreads hot keys if hash is good (MurmurHash recommended).
- Worst case: all hot keys same segment → profile and increase segment count.

---

## 5. Read path (GET)

```
1. segment = segmentFor(key)
2. entry = segment.data.get(key)   // CHM get, lock-free
3. if entry == null → miss
4. if entry.expired(now) → remove(key), expire metric++, miss
5. eviction.onAccess(key, entry)   // may need evictionLock.write (LRU move)
6. hit++, return value
```

**Lazy expiration** on read prevents serving stale data without waiting for background worker.

---

## 6. Write path (SET)

```
1. Validate size limits
2. segment = segmentFor(key)
3. Compute entrySize = overhead + key.len + value.len
4. segment.evictionLock.writeLock():
     while usedBytes + entrySize > maxBytes:
         victims = eviction.selectVictims(batchSize)
         remove victims from data + policy
     put key → entry in CHM
     eviction.onInsert(key, entry)
     TTL index register(expireAt, key)
5. usedBytes += delta
```

**Atomicity:** Entire step 4 under write lock for that segment — readers see consistent state.

---

## 7. Memory accounting

```java
static final int ENTRY_OVERHEAD = 64; // object headers, references (tune empirically)

long entryBytes(Key k, byte[] v) {
    return ENTRY_OVERHEAD + k.length() + v.length;
}
```

Global `AtomicLong usedBytes` updated on put/delete/evict.

**Max memory policy:**

| Mode | Behavior |
|------|----------|
| `evict` | Run policy until under cap |
| `noeviction` | Return `-ERR OOM` on SET |

Default: `evict` with LRU.

---

## 8. DELETE / EXISTS / EXPIRE / TTL

- **DELETE:** write lock → remove from CHM, eviction, TTL index, decrement bytes
- **EXISTS:** read path without returning value; still lazy-expire
- **EXPIRE:** update `expireAt` on entry; reschedule TTL bucket
- **TTL:** compute `(expireAt - now) / 1000` seconds; sentinel values per protocol

---

## 9. Data structures diagram

```
SegmentedCacheEngine
├── segments[256]
│   └── Segment
│       ├── ConcurrentHashMap<Key, CacheEntry>
│       └── EvictionPolicy state (linked structures)
├── TtlIndex (global or per-segment)
└── AtomicLong usedBytes, metrics
```

---

## 10. Persistence (later phase)

Not in Phase 1–3. Planned:

- **Snapshot:** periodic consistent point-in-time (fork segment iterators under lock)
- **AOF:** append SET/DEL to log for durability tradeoff

Storage engine exposes `snapshot()` hook without coupling Netty to disk IO — run snapshot on separate `ScheduledExecutorService`.

---

## 11. Performance targets (guidance)

On developer laptop (single node, Phase 3):

- **GET hit:** aim &lt; 50µs p99 in-process (no network) via JMH
- **GET over TCP:** dominated by loopback + parsing; document ~100µs–1ms realistic

Never quote numbers without JMH evidence — architecture doc sets targets, benchmarks prove them.

---

*Implementation: `helix-storage` module.*
