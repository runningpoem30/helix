# Cache-Aside Pattern (Helix Examples)

How production systems use caches in front of slower data stores — and how `helix-examples` demonstrates it.

---

## 1. Pattern overview

```
Application receives request for resource R (key K)
        │
        ▼
   cache.get(K)
        │
   ┌────┴────┐
   │ HIT?    │
   └────┬────┘
     yes│      no
        │       │
        ▼       ▼
    return   db.fetch(K)
    value         │
                  ▼
              cache.set(K, value, TTL)
                  │
                  ▼
              return value
```

**Application code owns consistency** — the cache does not write through to the DB automatically.

---

## 2. Why caches use this pattern

| Benefit | Explanation |
|---------|-------------|
| Simple | Cache is a side dependency, easy to disable |
| Protects DB | Absorbs read traffic on hot keys |
| Flexible TTL | Per-key freshness without DB schema change |

| Risk | Mitigation |
|------|------------|
| Stale reads | Short TTL, explicit invalidation on writes |
| Cache stampede | Singleflight / request coalescing (future) |
| Thundering herd on expiry | Jittered TTL, proactive refresh |

---

## 3. Helix example module (`helix-examples`)

### Simulated database

```java
class SimulatedDatabase {
    CompletableFuture<String> findById(String id) {
        return CompletableFuture.supplyAsync(() -> {
            Thread.sleep(30 + ThreadLocalRandom.current().nextInt(20));
            return "User-" + id;
        });
    }
}
```

### Cache-aside service

```java
class UserService {
    Optional<String> getUser(String id) {
        String key = "user:" + id;
        Optional<String> cached = helixClient.get(key);
        if (cached.isPresent()) {
            metrics.hit();
            return cached;
        }
        metrics.miss();
        String fromDb = db.findById(id).join();
        helixClient.set(key, fromDb, Duration.ofMinutes(5));
        return Optional.of(fromDb);
    }
}
```

### Demo output

```
GET user:42 → miss → DB 48ms → SET → User-42
GET user:42 → hit → 0.4ms
Hit ratio: 66.7% (2 requests)
```

---

## 4. Interview talking points

- **Read-through** vs **cache-aside:** who populates on miss (cache vs app)
- **Write-through / write-behind** for write paths
- **Invalidation:** `DELETE user:42` on profile update
- Helix role: **opaque fast KV** — not a magic DB accelerator without app logic

---

## 5. Extension exercises

1. Add stampede protection with `ConcurrentHashMap` of in-flight `CompletableFuture`
2. Compare hit ratio with Zipfian vs uniform key access
3. Measure DB QPS with and without Helix at 100 concurrent clients

---

*Implement in Phase 4 alongside TCP client helper in `helix-examples`.*
