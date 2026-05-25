# Helix — Recruiter & Interview Demo Strategy

How to present Helix so it reads as **systems engineering**, not another Spring CRUD project.

---

## 1. Elevator pitch (30 seconds)

> "Helix is a high-performance in-memory cache server I built in Java 21. Clients talk over raw TCP using a text protocol — I used Netty for non-blocking IO. The storage layer is sharded for concurrency, with TTL expiration and pluggable eviction — LRU, LFU, and FIFO. I benchmarked with JMH and a multi-client load tool. It's designed like infrastructure middleware, not a REST API."

---

## 2. What recruiters scan for

| Signal | Where in Helix |
|--------|----------------|
| Concurrency | Segmented CHM + RW locks doc |
| Networking | Netty pipeline, TCP protocol |
| Performance | JMH + load driver results |
| Real-world patterns | Cache-aside example |
| Ops awareness | Docker, GCP doc, config env vars |

**GitHub README first 10 seconds:** architecture diagram + telnet demo + one benchmark number.

---

## 3. Five-minute live demo script

### Setup (pre-open terminal)

```bash
docker compose -f docker/docker-compose.yml up -d
```

### Act 1 — Protocol (90 sec)

```bash
telnet localhost 6379
PING
SET user:1 Arya
GET user:1
EXISTS user:1
DELETE user:1
```

**Say:** "No HTTP — this is wire-protocol infrastructure like Redis/Memcached."

### Act 2 — TTL (60 sec)

```bash
SET session:abc secret EX 3
TTL session:abc
# wait
GET session:abc
```

**Say:** "Lazy expiration on read plus a background bucket worker — hybrid like production caches."

### Act 3 — Eviction (90 sec)

```bash
INFO
# show used_memory, evictions
# pre-load script that fills memory
```

**Say:** "When we exceed maxmemory, LRU evicts coldest keys per segment — O(1) touch."

### Act 4 — Cache-aside (60 sec)

```bash
cd helix-examples && mvn exec:java
```

**Say:** "On miss we simulate DB latency, populate cache, next request hits — measure hit ratio in logs."

---

## 4. Deep-dive questions (be ready)

### "Why Netty?"

Non-blocking IO, many connections, fine-grained pipeline control — Tomcat is the wrong abstraction for a custom TCP cache protocol.

### "How do you handle concurrency?"

256 segments, ConcurrentHashMap reads, read-write lock for eviction consistency, optional executor offload so Netty threads don't block.

### "LRU vs LFU?"

LRU: recency, simple. LFU: frequency, resists scan pollution. FIFO: insertion order. Give workload example for each.

### "What breaks first under load?"

Segment lock contention on hot keys; then GC if allocating Strings per request; document mitigations from PERFORMANCE.md.

### "How is this different from Redis?"

Smaller scope — learning vehicle demonstrating JVM concurrency and protocol design, not production replacement.

### "CAP theorem?"

Single node: CP for single key ops. Cluster phase: partition-tolerant AP cache with eventual replication.

---

## 5. Resume bullet templates

- Built **Helix**, a TCP-based distributed in-memory cache engine in **Java 21/Netty** with segmented concurrent storage, **TTL expiration**, and **LRU/LFU/FIFO** eviction policies.
- Implemented custom **wire protocol** and Netty pipeline achieving **X ops/sec** (loopback, Y clients) with **P99 Z µs** latency.
- Designed **cache-aside** integration example and benchmarked via **JMH** and multi-connection load tests.

Replace X/Y/Z with real Phase 4 numbers.

---

## 6. Portfolio artifacts

| Artifact | Purpose |
|----------|---------|
| 2-min screen recording | LinkedIn / resume link |
| Architecture PNG | README |
| Benchmark table | Credibility |
| `docs/EVICTION.md` link | Shows depth |

---

## 7. Anti-patterns (don't say)

- "It's like Redis but better"
- "Microservices architecture" (it's one process)
- "AI-powered" anything
- Unbenchmarked "millions of ops" claims

---

## 8. Suggested repo layout for reviewers

1. README diagram
2. `docs/ARCHITECTURE.md` (1 page summary)
3. `helix-server` entrypoint
4. `helix-eviction` policy code
5. `helix-benchmark/results/latest.json`

---

## 9. Follow-up project story (internship narrative)

> "Phase 1 proved protocol + storage. Phase 2 added TTL because memory reclamation is non-trivial. Phase 3 eviction policies taught me policy tradeoffs. Phase 4 JMH proved where contention actually was. Phase 5 would add consistent hashing — I documented the design before implementing."

Shows **judgment** and **phased delivery** — what teams want in interns.

---

*Practice the telnet demo until it's muscle memory.*
