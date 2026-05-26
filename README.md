# Helix

High-performance distributed in-memory cache engine — built for systems engineering.

Helix is infrastructure software: a TCP-native, Netty-driven cache server with segmented concurrent storage, TTL expiration, pluggable eviction (LRU/LFU/FIFO), metrics, and cluster routing.

## What this is

| Helix is | Helix is not |
|----------|----------------|
| TCP protocol + Netty event-loop server | REST-first Spring Boot app |
| In-memory KV engine with eviction/TTL | Redis tutorial clone |
| Concurrency + memory engineering showcase | Generic microservice demo |

## Documentation

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) and [docs/ROADMAP.md](docs/ROADMAP.md).

## Quick start

```bash
mvn -q -pl helix-server -am package
java -jar helix-server/target/helix-server.jar
```

```bash
telnet localhost 6379
PING
SET user:1 Arya
GET user:1
SET session:xyz secret EX 300
TTL session:xyz
EXPIRE user:1 60
INFO
QUIT
```

## Commands

| Command | Example |
|---------|---------|
| PING | `PING` |
| SET | `SET key value` or `SET key value EX 60` |
| GET | `GET key` |
| DELETE | `DELETE key` / `DEL key` |
| EXISTS | `EXISTS key` |
| EXPIRE | `EXPIRE key 60` |
| TTL | `TTL key` |
| INFO | `INFO` |
| QUIT | `QUIT` |

## Configuration (environment)

| Variable | Default | Description |
|----------|---------|-------------|
| `HELIX_BIND` | 127.0.0.1 | Listen address |
| `HELIX_PORT` | 6379 | TCP port |
| `HELIX_MAXMEMORY` | 256mb | Eviction threshold |
| `HELIX_EVICTION` | lru | `lru`, `lfu`, or `fifo` |
| `HELIX_SEGMENTS` | 256 | Shard count (power of 2) |
| `HELIX_TTL_TICK_MS` | 100 | Background expiration interval |
| `HELIX_NODE_ID` | node-1 | Node identity (cluster) |
| `HELIX_CLUSTER_PEERS` | — | Comma-separated `host:port` for client-side routing |
| `HELIX_REPLICA_PEERS` | — | Async replication targets |

## Repository layout

```
helix/
├── helix-core/       # Commands, responses, config, protocol
├── helix-storage/    # Segmented engine, TTL, expiration worker
├── helix-eviction/   # LRU, LFU, FIFO policies
├── helix-network/    # Netty pipeline
├── helix-metrics/    # Hit ratio, latency percentiles
├── helix-cluster/    # Consistent hash, replication, TCP client
├── helix-server/     # JVM entrypoint
├── helix-benchmark/  # JMH + TCP load driver
├── helix-examples/   # Cache-aside demo
├── helix-cli/        # Interactive TCP client
└── docs/             # Architecture & design
```

## Benchmarks & examples

```bash
# JMH in-process GET throughput
mvn -q -pl helix-benchmark -am package
java -jar helix-benchmark/target/helix-benchmark.jar  # LoadDriver default main

# TCP load test (server must be running)
java -cp helix-benchmark/target/helix-benchmark.jar io.helix.benchmark.LoadDriver localhost 6379 20 30 90

# Cache-aside demo
mvn -q -pl helix-examples exec:java
```

## Docker

```bash
docker compose -f docker/docker-compose.yml up --build
# Multi-node cluster prototype:
docker compose -f docker/docker-compose.cluster.yml up --build
```




## License

TBD — add before public release.
