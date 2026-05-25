# Helix Deployment

Helix ships as a single JVM process in Docker. No Kubernetes required for credible demos.

---

## 1. Local Docker

### Dockerfile (multi-stage)

```dockerfile
# Stage 1: build
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY pom.xml .
COPY helix-*/pom.xml helix-*/
RUN mvn -q -pl helix-server -am package -DskipTests

# Stage 2: runtime
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/helix-server/target/helix-server-*.jar app.jar
EXPOSE 6379
ENV HELIX_PORT=6379 HELIX_BIND=0.0.0.0 HELIX_MAXMEMORY=256mb
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### Docker Compose

```yaml
services:
  helix:
    build: .
    ports:
      - "6379:6379"
    environment:
      HELIX_MAXMEMORY: 512mb
      HELIX_EVICTION: lru
    deploy:
      resources:
        limits:
          memory: 768M
    healthcheck:
      test: ["CMD", "bash", "-c", "echo PING | nc localhost 6379"]
      interval: 10s
      timeout: 3s
      retries: 3
```

**Run:**

```bash
docker compose -f docker/docker-compose.yml up --build
telnet localhost 6379
```

---

## 2. Configuration (environment)

| Variable | Default | Description |
|----------|---------|-------------|
| `HELIX_PORT` | 6379 | TCP listen port |
| `HELIX_BIND` | 127.0.0.1 | Bind address |
| `HELIX_MAXMEMORY` | 256mb | Eviction threshold |
| `HELIX_EVICTION` | lru | Policy name |
| `HELIX_SEGMENTS` | 256 | Shard count |
| `HELIX_WORKER_THREADS` | 2×cores | Netty workers |
| `HELIX_TTL_TICK_MS` | 100 | Expiration worker |

---

## 3. Google Cloud VM deployment

### Architecture

```
Internet / Office
       │
       ▼
┌──────────────────┐
│ GCP Firewall      │ tcp:6379 (restrict to your IP)
└────────┬─────────┘
         ▼
┌──────────────────┐
│ e2-standard-4 VM  │
│ Ubuntu 22.04      │
│ Docker Engine     │
│  helix container  │
└──────────────────┘
```

### Steps

1. Create VM: e2-standard-4, 30GB disk, Ubuntu LTS
2. Install Docker + Compose
3. `git clone` / copy image from GHCR
4. `docker compose up -d`
5. Firewall: allow 6379 only from demo IP
6. SSH tunnel alternative for safer demos: `ssh -L 6379:localhost:6379 vm`

### Sizing guidance

| Workload | VM | Container maxmemory |
|----------|-----|---------------------|
| Demo / interview | e2-medium | 512mb |
| Load test | e2-standard-4 | 2–4gb |
| Cluster node (Phase 5) | e2-standard-8 | 8gb+ |

---

## 4. JVM flags (production-oriented)

```bash
java -jar app.jar \
  -Xms512m -Xmx512m \
  -XX:+UseG1GC \
  -XX:MaxGCPauseMillis=50 \
  -XX:+AlwaysPreTouch
```

**Rationale:** Fixed heap avoids resize pauses during demos; G1 default on JDK 21; `AlwaysPreTouch` stabilizes first-request latency.

---

## 5. Observability (later)

- Prometheus JMX exporter sidecar (optional Spring admin)
- GCP Ops Agent for RSS/CPU dashboards
- Structured logs: JSON lines with `op`, `latency_us`, `result`

---

## 6. High availability (Phase 5 preview)

- 3+ nodes behind client-side consistent hash
- No single coordinator — AP-oriented cache semantics
- Replication: async primary → replica (eventual)

---

## 7. Security checklist before public VM

- [ ] Bind to private IP or SSH tunnel only
- [ ] No `0.0.0.0` on public internet without AUTH (Phase 6)
- [ ] Firewall source IP restricted
- [ ] Auto-shutdown VM after demo (cost + safety)

---

*Artifacts in `docker/` directory; wired in Phase 1 completion.*
