package io.helix.core;

import java.net.InetSocketAddress;

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
        int workerThreads
) {

    public static final String VERSION = "0.1.0";

    /** Minimal config for unit/integration tests. */
    public static HelixConfig forTest(int port) {
        return new HelixConfig("127.0.0.1", port, 4, 65_536, 512, 512 * 1024, false, 2);
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
                envInt("HELIX_WORKER_THREADS", Runtime.getRuntime().availableProcessors() * 2)
        );
    }

    public InetSocketAddress bindAddress() {
        return new InetSocketAddress(bindHost, port);
    }

    public int segmentMask() {
        int segments = roundUpToPowerOfTwo(segmentCount);
        return segments - 1;
    }

    public int segmentCountPowerOfTwo() {
        return roundUpToPowerOfTwo(segmentCount);
    }

    private static int roundUpToPowerOfTwo(int value) {
        int n = Integer.highestOneBit(Math.max(1, value));
        return n == value ? n : n << 1;
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
