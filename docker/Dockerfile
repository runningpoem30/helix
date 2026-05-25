# Helix — multi-stage build (active after Phase 1)
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY pom.xml .
COPY helix-core/pom.xml helix-core/
COPY helix-storage/pom.xml helix-storage/
COPY helix-eviction/pom.xml helix-eviction/
COPY helix-network/pom.xml helix-network/
COPY helix-metrics/pom.xml helix-metrics/
COPY helix-server/pom.xml helix-server/
COPY helix-benchmark/pom.xml helix-benchmark/
COPY helix-examples/pom.xml helix-examples/
COPY helix-cli/pom.xml helix-cli/
# Source COPY added in Phase 1 when modules compile
RUN test -f pom.xml

FROM eclipse-temurin:21-jre
WORKDIR /app
# COPY --from=build /app/helix-server/target/helix-server.jar app.jar
EXPOSE 6379
ENV HELIX_PORT=6379 HELIX_BIND=0.0.0.0 HELIX_MAXMEMORY=256mb HELIX_EVICTION=lru
# ENTRYPOINT ["java", "-jar", "app.jar"]
CMD ["echo", "Helix image builds after Phase 1 implementation"]
