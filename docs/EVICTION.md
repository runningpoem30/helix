# Helix Eviction Policies

When `usedBytes > maxmemory`, Helix evicts keys according to a pluggable policy. This module is a primary interview differentiator — implement correctly and explain complexity.

---

## 1. Policy interface

```java
public interface EvictionPolicy {
    String name();
    void onInsert(Key key);
    void onAccess(Key key);
    void onRemove(Key key);
    /** Return up to count keys to evict (may return fewer) */
    List<Key> selectVictims(int count);
    int size();
}
```

`EvictionPolicyFactory` reads config: `helix.eviction=lru|lfu|fifo`.

---

## 2. LRU — Least Recently Used

### Intent

Evict the key not accessed for the longest time.

### Implementation A: `LinkedHashMap` (access-order)

```java
class LruPolicy implements EvictionPolicy {
    private final LinkedHashMap<Key, Boolean> order =
        new LinkedHashMap<>(16, 0.75f, true); // accessOrder=true

    List<Key> selectVictims(int n) {
        Iterator<Key> it = order.keySet().iterator();
        // first n keys = LRU victims
    }
}
```

| Operation | Time | Notes |
|-----------|------|-------|
| onAccess | O(1) | moves to tail |
| onInsert | O(1) | |
| selectVictims | O(n) | n = victims requested |

**Memory:** O(N) for map entries + linked list pointers (~ 32 bytes overhead per key beyond map).

### Implementation B: Doubly-linked list + HashMap

- Industry standard when you need O(1) remove arbitrary key (DELETE)
- `HashMap<Key, Node>` + head/tail sentinel nodes

### Approximate LRU (optional optimization)

- On access, promote only if `random() % 10 == 0`
- Reduces lock hold time; slightly worse hit rate

**Interview line:** "Exact LRU under per-segment lock; we can approximate to reduce write lock contention on read-heavy workloads."

---

## 3. LFU — Least Frequently Used

### Intent

Evict keys with lowest access frequency; protects frequently used items from one-time scans.

### Implementation: frequency buckets (recommended)

```
Map<Key, Node> nodes
Map<Integer, DoublyLinkedList> freqBuckets
int minFreq
```

- On access: increment node freq, move to bucket `freq+1`
- Evict: take key from `minFreq` bucket's LRU tail

| Operation | Time |
|-----------|------|
| onAccess | O(1) |
| onInsert | O(1) (freq=1) |
| selectVictims | O(1) per victim |

**Memory:** Higher than LRU — freq integer + bucket list per node.

### Approximate LFU (Count-Min sketch style) — Phase 4+

For millions of keys, exact LFU memory grows; document Redis LFU logarithmic counter approach as future work.

---

## 4. FIFO — First In First Out

### Intent

Evict oldest **inserted** key regardless of access (useful for queue-like workloads).

### Implementation

```
ArrayDeque<Key> queue
HashMap<Key, Boolean> index  // invalidation on remove
```

| Operation | Time |
|-----------|------|
| onInsert | O(1) enqueue |
| onAccess | no-op |
| selectVictims | O(1) dequeue head |

**Caveat:** DELETE must remove from queue — use `index` map + mark tombstone or lazy skip on select.

---

## 5. Policy comparison

| Policy | Hit rate (typical web cache) | Scan resistance | Implementation |
|--------|------------------------------|-----------------|----------------|
| LRU | Strong | Weak (one-shot scans pollute) | Simpler |
| LFU | Strong for hot sets | Strong | More complex |
| FIFO | Weakest general-purpose | N/A | Simplest |

**Workload guidance:**

- General API caching → **LRU**
- Skewed hot key distribution → **LFU**
- Stream ingestion / time series window → **FIFO**

---

## 6. Eviction trigger flow

```
SET arrives
  newSize = usedBytes + entrySize
  while newSize > maxBytes:
    victims = policy.selectVictims(evictionBatch)  // default batch 16
    for v in victims:
      remove from CHM
      policy.onRemove(v)
      newSize -= size(v)
    evictions_metric += victims.size
  if still over limit && policy.size()==0:
    return OOM error
```

**Batch eviction:** Amortizes lock overhead; avoids one-key-at-a-time thrashing.

---

## 7. Per-segment vs global policy

| Model | Pros | Cons |
|-------|------|------|
| Per-segment LRU | Parallel eviction | Not global LRU order |
| Global LRU | True LRU | Hot lock |

**Helix default:** Per-segment policies (same as Redis approximate shard LRU spirit). Document in README.

---

## 8. Testing eviction

- Unit: insert N keys, maxmemory for N-1, assert victim order deterministic with fixed seed
- Integration: SET until eviction, GET confirms hot key survived (LRU)

---

## 9. Time complexity cheat sheet (interview)

```
LRU:  access O(1), evict O(k) for k victims
LFU:  access O(1), evict O(k)
FIFO: insert O(1), evict O(k)
```

Space: **O(N)** for all exact policies.

---

*Implementation: `helix-eviction` module, Phase 3.*
