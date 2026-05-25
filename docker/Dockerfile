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
COPY helix-cluster/pom.xml helix-cluster/
COPY helix-core/src helix-core/src
COPY helix-storage/src helix-storage/src
COPY helix-eviction/src helix-eviction/src
COPY helix-network/src helix-network/src
COPY helix-metrics/src helix-metrics/src
COPY helix-server/src helix-server/src
COPY helix-cluster/src helix-cluster/src
RUN apt-get update && apt-get install -y maven && \
    mvn -q -pl helix-server -am package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/helix-server/target/helix-server.jar app.jar
EXPOSE 6379
ENV HELIX_PORT=6379 HELIX_BIND=0.0.0.0 HELIX_BANNER=true
ENTRYPOINT ["java", "-jar", "app.jar"]
