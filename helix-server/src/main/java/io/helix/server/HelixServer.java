package io.helix.server;

import io.helix.core.HelixConfig;
import io.helix.metrics.CacheMetrics;
import io.helix.network.NettyHelixServer;
import io.helix.storage.CacheEngine;
import io.helix.storage.CacheService;
import io.helix.storage.SegmentedCacheEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JVM entrypoint for the Helix cache server.
 */
public final class HelixServer {

    private static final Logger log = LoggerFactory.getLogger(HelixServer.class);

    public static void main(String[] args) throws Exception {
        HelixConfig config = HelixConfig.fromEnvironment();
        CacheMetrics metrics = new CacheMetrics();
        CacheEngine engine = new SegmentedCacheEngine(config);
        CacheService cacheService = new CacheService(engine, metrics);

        NettyHelixServer server = new NettyHelixServer(config, cacheService);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutting down...");
            server.close();
        }, "helix-shutdown"));

        server.start();
        log.info("Ready — connect with: telnet {} {}", config.bindHost(), config.port());
        server.awaitShutdown();
    }
}
