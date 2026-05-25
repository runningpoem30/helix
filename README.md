# Helix

High-performance distributed in-memory cache engine — built for systems engineering, not CRUD.

Helix is infrastructure software: a TCP-native, Netty-driven cache server with explicit concurrency design, TTL expiration, pluggable eviction, and a path to clustering.

## What this is

| Helix is | Helix is not |
|----------|----------------|
| TCP protocol + Netty event-loop server | REST-first Spring Boot app |
| In-memory KV engine with eviction/TTL | Redis tutorial clone |
| Concurrency + memory engineering showcase | Generic microservice demo |

## Quick start (after Phase 1)

```bash
# Build
mvn -q -pl helix-server -am package

# Run (default port 6379)
java -jar helix-server/target/helix-server.jar

# Demo
telnet localhost 6379
SET user:1 Arya
GET user:1
```

## Repository layout

```
helix/
├── helix-core/          # Domain types, commands, responses, config
├── helix-storage/       # In-memory engine, TTL, concurrent access
├── helix-eviction/      # LRU, LFU, FIFO policies
├── helix-network/       # Netty pipeline, protocol codec, handlers
├── helix-server/        # JVM entrypoint, wiring, lifecycle
├── helix-metrics/       # Counters, latency histograms (Phase 4)
├── helix-benchmark/       # JMH + load drivers (Phase 4)
├── helix-examples/      # Cache-aside demo client
├── helix-cli/           # Optional terminal client
├── docker/              # Images, Compose
└── docs/                # Architecture & design (start here)
```

## Documentation (read in order)

1. [Architecture](docs/ARCHITECTURE.md) — system overview, data flow, phases
2. [TCP Protocol](docs/PROTOCOL.md) — wire format, commands, errors
3. [Netty Design](docs/NETTY.md) — event loops, pipeline, handlers
4. [Storage Engine](docs/STORAGE.md) — entries, sharding, memory accounting
5. [Concurrency](docs/CONCURRENCY.md) — locks, stripes, read/write paths
6. [TTL Engine](docs/TTL.md) — expiration strategies
7. [Eviction](docs/EVICTION.md) — LRU/LFU/FIFO algorithms & tradeoffs
8. [Benchmarking](docs/BENCHMARKING.md) — workloads, JMH, metrics
9. [Deployment](docs/DEPLOYMENT.md) — Docker, GCP VM, scaling
10. [Roadmap](docs/ROADMAP.md) — phased implementation plan
11. [Recruiter Demo](docs/RECRUITER_DEMO.md) — how to present Helix in interviews

## Tech stack

- **Java 21** — virtual threads optional later; records, sealed types for commands
- **Netty 4.x** — non-blocking TCP server
- **Maven** multi-module monorepo
- **JMH** (Phase 4) — microbenchmarks
- **Docker / Compose** — local & cloud deployment

Spring Boot is intentionally **out of the hot path**. Optional later: `helix-admin` for metrics HTTP if needed.

## Implementation status

| Phase | Scope | Status |
|-------|--------|--------|
| 0 | Architecture & scaffold | **Current** |
| 1 | Netty TCP, SET/GET, storage | Planned |
| 2 | TTL, concurrency hardening | Planned |
| 3 | Eviction policies, memory limits | Planned |
| 4 | Metrics, JMH benchmarks | Planned |
| 5 | Distribution, replication | Planned |

## License

TBD — add before public release.
