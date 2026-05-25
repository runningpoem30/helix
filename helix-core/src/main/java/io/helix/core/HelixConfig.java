package io.helix.core;

import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.List;

/**
 * Server configuration loaded from environment variables with sensible defaults.
 */
public record HelixConfig(
        String bindHost,
        int port,
        int segmentCount,
        int maxCommandBytes,
        int maxKeyBytes,
        int maxValueBytes,
        boolean sendBanner,
        int workerThreads,
        long maxMemoryBytes,
        String evictionPolicy,
        int ttlTickMs,
        int ttlResolutionMs,
        int storageThreads,
        String nodeId,
        List<String> clusterPeers,
        List<String> replicaPeers
) {

    public static final String VERSION = "0.2.0";

    public static HelixConfig forTest(int port) {
        return new HelixConfig(
                "127.0.0.1", port, 4, 65_536, 512, 512 * 1024, false, 2,
                10 * 1024 * 1024, "lru", 50, 50, 2,
                "node-test", List.of(), List.of());
    }

    public static HelixConfig fromEnvironment() {
        return new HelixConfig(
                env("HELIX_BIND", "127.0.0.1"),
                envInt("HELIX_PORT", 6379),
                envInt("HELIX_SEGMENTS", 256),
                envInt("HELIX_MAX_COMMAND_BYTES", 65_536),
                envInt("HELIX_MAX_KEY_BYTES", 512),
                envInt("HELIX_MAX_VALUE_BYTES", 512 * 1024),
                envBool("HELIX_BANNER", true),
                envInt("HELIX_WORKER_THREADS", Runtime.getRuntime().availableProcessors() * 2),
                parseMemory(env("HELIX_MAXMEMORY", "256mb")),
                env("HELIX_EVICTION", "lru").toLowerCase(),
                envInt("HELIX_TTL_TICK_MS", 100),
                envInt("HELIX_TTL_RESOLUTION_MS", 100),
                envInt("HELIX_STORAGE_THREADS", Runtime.getRuntime().availableProcessors() * 2),
                env("HELIX_NODE_ID", "node-1"),
                parseList(env("HELIX_CLUSTER_PEERS", "")),
                parseList(env("HELIX_REPLICA_PEERS", "")));
    }

    public InetSocketAddress bindAddress() {
        return new InetSocketAddress(bindHost, port);
    }

    public int segmentCountPowerOfTwo() {
        return roundUpToPowerOfTwo(segmentCount);
    }

    public boolean isClustered() {
        return !clusterPeers.isEmpty();
    }

    public boolean hasReplicas() {
        return !replicaPeers.isEmpty();
    }

    private static int roundUpToPowerOfTwo(int value) {
        int n = Integer.highestOneBit(Math.max(1, value));
        return n == value ? n : n << 1;
    }

    static long parseMemory(String spec) {
        String s = spec.trim().toLowerCase();
        long multiplier = 1;
        if (s.endsWith("kb")) {
            multiplier = 1024;
            s = s.substring(0, s.length() - 2);
        } else if (s.endsWith("mb")) {
            multiplier = 1024 * 1024;
            s = s.substring(0, s.length() - 2);
        } else if (s.endsWith("gb")) {
            multiplier = 1024L * 1024 * 1024;
            s = s.substring(0, s.length() - 2);
        }
        return Long.parseLong(s.trim()) * multiplier;
    }

    private static List<String> parseList(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return Arrays.stream(csv.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    private static String env(String key, String defaultValue) {
        String v = System.getenv(key);
        return v == null || v.isBlank() ? defaultValue : v.trim();
    }

    private static int envInt(String key, int defaultValue) {
        String v = System.getenv(key);
        if (v == null || v.isBlank()) {
            return defaultValue;
        }
        return Integer.parseInt(v.trim());
    }

    private static boolean envBool(String key, boolean defaultValue) {
        String v = System.getenv(key);
        if (v == null || v.isBlank()) {
            return defaultValue;
        }
        return Boolean.parseBoolean(v.trim());
    }
}
