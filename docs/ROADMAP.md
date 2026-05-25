# Helix Implementation Roadmap

Incremental delivery. Each phase ends with a **demoable** terminal session and tests.

---

## Phase 0 — Architecture & scaffold ✅

**Deliverables:**

- [x] Architecture docs (`docs/`)
- [x] Maven multi-module skeleton
- [x] Docker/Compose templates
- [ ] README badges after CI exists

**Exit criteria:** Another engineer can read docs and implement Phase 1 without ambiguity.

---

## Phase 1 — TCP + basic storage ✅

### Scope

- Netty server on port 6379
- Commands: `PING`, `SET`, `GET`, `DELETE`, `EXISTS`, `QUIT`
- `SegmentedCacheEngine` with `ConcurrentHashMap` only (no TTL/eviction)
- Single-module integration test: Netty client sends SET/GET

### Tasks

| # | Task | Module | Status |
|---|------|--------|--------|
| 1 | Parent POM, Java 21, dependency management | root | ✅ |
| 2 | `Command` / `Response` types | helix-core | ✅ |
| 3 | `HelixLineDecoder`, `HelixCommandDecoder`, encoder | helix-network | ✅ |
| 4 | `HelixServer`, `HelixCommandHandler` | helix-server | ✅ |
| 5 | `SegmentedCacheEngine` minimal | helix-storage | ✅ |
| 6 | `HelixMain` + config from env | helix-server | ✅ |
| 7 | Unit tests for parser + storage | all | ✅ |
| 8 | `docker/Dockerfile` builds | docker | ✅ |

### Demo

```
telnet localhost 6379
SET user:1 Arya
GET user:1
→ Arya
```

---

## Phase 2 — TTL + concurrency (1–2 weeks)

### Scope

- `SET ... EX n`, `EXPIRE`, `TTL`
- Lazy expiration on read
- `ExpirationWorker` + TTL index
- Per-segment `ReentrantReadWriteLock` for policy-ready writes
- Storage executor offload from Netty

### Demo

```
SET token abc EX 5
TTL token
→ :5
(sleep 6)
GET token
→ $-1
```

---

## Phase 3 — Eviction + memory limits (2 weeks)

### Scope

- `helix-eviction`: LRU, LFU, FIFO
- `HELIX_MAXMEMORY` enforcement
- `INFO` basic stats
- Integration tests: fill until eviction, assert hot key survives (LRU)

### Demo

```
# maxmemory tiny, SET many keys, show evictions in INFO
```

---

## Phase 4 — Metrics + benchmarking (1–2 weeks)

### Scope

- `helix-metrics`: hit ratio, latency histogram
- JMH benchmarks
- TCP load driver
- `helix-examples` cache-aside demo
- Results documented in README

### Demo

Run benchmark CLI, paste throughput in README with graphs.

---

## Phase 5 — Distribution (3+ weeks)

### Scope

- Consistent hash client router
- Multi-node Docker Compose
- Async replication prototype
- Cluster `INFO` per node

**Not required for internship portfolio** if Phases 1–4 are strong.

---

## Phase 6+ — Optional

- AUTH token
- TLS termination docs
- Snapshot persistence
- Spring Boot `helix-admin` metrics UI only

---

## Testing strategy per phase

| Layer | Tool |
|-------|------|
| Parser | JUnit parameterized |
| Storage / eviction | JUnit + deterministic policies |
| Network | Netty `EmbeddedChannel` |
| End-to-end | Testcontainers or local process spawn |

---

## Risk register

| Risk | Mitigation |
|------|------------|
| Scope creep (Redis compat) | Stick to documented protocol |
| Lock bugs | Stress test + segment invariants |
| Demo flakiness | Fixed TTL sleeps → use EX 2 + poll |

---

## Definition of "portfolio ready"

- [ ] 5 min telnet demo video or GIF
- [ ] Architecture diagram in README
- [ ] One benchmark table with honest hardware note
- [ ] Cache-aside example code
- [ ] LinkedIn post: "built a TCP cache engine in Java/Netty"

---

*Start Phase 1 implementation on request.*
