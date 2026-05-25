# Helix TTL Expiration Engine

Time-to-live is a defining feature of caches. Helix implements **lazy** and **active** expiration with clear tradeoffs.

---

## 1. Semantics

| State | TTL command | GET behavior |
|-------|-------------|--------------|
| No TTL | `:-1` | Returns value until deleted |
| TTL set | `:n` seconds | Expires at `now + n` |
| Missing key | `:-2` | `$-1` nil |
| Expired | `:-2` after lazy delete | `$-1` nil |

`SET key value EX seconds` sets expire-at on write.

---

## 2. Representation

```java
record CacheEntry(..., long expireAtEpochMs) {
    boolean isExpired(long now) {
        return expireAtEpochMs > 0 && now >= expireAtEpochMs;
    }
}
```

`expireAtEpochMs == 0` means persistent (until evicted).

---

## 3. Lazy expiration

**When:** Every `GET`, `EXISTS`, and optionally `TTL`.

**Steps:**

1. Load entry
2. If `isExpired(now)` → delete key, increment `expired_lazy` metric, return miss

**Pros:**

- Zero background CPU if keys are never read again
- Always correct at access time

**Cons:**

- **Memory drag:** "Dead" keys occupy RAM until accessed or actively collected
- Cold keys never read → need active policy

---

## 4. Active expiration (background worker)

### Algorithm: time-bucket index

Divide time into slots of width `resolutionMs` (e.g. 100ms):

```
bucketId = expireAt / resolutionMs
TtlWheel[bucketId % W] → Set<Key>   // W = wheel size, e.g. 4096 slots
```

**Worker loop** (`ScheduledExecutorService`):

```
every tickMs:
  currentBucket = now / resolutionMs
  for key in wheel[currentBucket % W]:
      if segment.get(key).expired:
          delete(key)
          expired_active++
  advance pointer
```

**Complexity per tick:** O(keys in bucket) — assumed small if resolution reasonable.

### Alternative: `ConcurrentSkipListMap<Long, Set<Key>>`

- Insert O(log N), scan range `[now, now+window]` each tick
- Simpler mental model for Phase 2 implementation

**Helix Phase 2 default:** Skip list for clarity; optimize to wheel if profiling shows index overhead.

---

## 5. Timing mechanisms

| Mechanism | Precision | CPU |
|-----------|-----------|-----|
| `ScheduledExecutorService.scheduleAtFixedRate` | ms-scale | low |
| Netty `HashedWheelTimer` | ms-scale | integrated with Netty |
| Busy spin | μs | ✗ wasteful |

Use **JDK scheduler** in `helix-storage` to keep Netty timer threads separate from TTL business logic.

**Tick interval tradeoff:**

| tickMs | CPU | Memory freshness |
|--------|-----|------------------|
| 10 | higher | faster reclaim |
| 250 | lower | stale RAM longer |

Default: **100ms** tick, **100ms** bucket resolution.

---

## 6. EXPIRE command

Updates existing entry's `expireAt` without changing value:

1. Lookup under write lock
2. Remove key from old TTL bucket
3. Insert into new bucket
4. Return `:1` or `:0`

---

## 7. Interaction with eviction

- Expired key is **logically absent** — counts as miss, not eviction
- Eviction policy should `onRemove` when TTL delete fires
- Memory freed → `usedBytes` decremented

Order: **expire check before eviction on GET**; on SET, new entry may replace expired slot in place.

---

## 8. Memory considerations

- TTL index stores **key references** only (not values) — overhead ~ key size + pointer per index entry
- Duplicate scheduling mistake → same key in two buckets: worker idempotently deletes

---

## 9. Cleanup tradeoffs summary

| Strategy | Memory | CPU | Correctness |
|----------|--------|-----|-------------|
| Lazy only | Poor for cold TTL keys | Minimal | Perfect on read |
| Active only | Good | Periodic spikes | Slight delay sub-tick |
| Lazy + Active | Best practical balance | Moderate | Production pattern |

Redis uses similar hybrid; cite in interviews.

---

## 10. Expiration-heavy benchmark scenario

- SET 1M keys with EX 60
- Stop reads
- Measure RAM vs time with active worker on/off
- Plot reclaim curve — strong demo graph for portfolio

---

*Implementation: `TtlService` + `ExpirationWorker` in `helix-storage`, Phase 2.*
