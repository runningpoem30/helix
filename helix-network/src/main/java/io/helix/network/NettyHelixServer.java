package io.helix.network;

import io.helix.core.HelixConfig;
import io.helix.storage.CacheService;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Netty TCP server bootstrap for Helix.
 */
public final class NettyHelixServer implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(NettyHelixServer.class);

    private final HelixConfig config;
    private final CacheService cacheService;
    private final ExecutorService storageExecutor;
    private final boolean ownsExecutor;

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;

    public NettyHelixServer(HelixConfig config, CacheService cacheService) {
        this(config, cacheService, Executors.newFixedThreadPool(config.storageThreads(), r -> {
            Thread t = new Thread(r, "helix-storage");
            t.setDaemon(true);
            return t;
        }), true);
    }

    public NettyHelixServer(
            HelixConfig config, CacheService cacheService, ExecutorService storageExecutor) {
        this(config, cacheService, storageExecutor, false);
    }

    private NettyHelixServer(
            HelixConfig config,
            CacheService cacheService,
            ExecutorService storageExecutor,
            boolean ownsExecutor) {
        this.config = config;
        this.cacheService = cacheService;
        this.storageExecutor = storageExecutor;
        this.ownsExecutor = ownsExecutor;
    }

    public void start() throws InterruptedException {
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup(config.workerThreads());

        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .option(ChannelOption.SO_BACKLOG, 1024)
                .childOption(ChannelOption.TCP_NODELAY, true)
                .childOption(ChannelOption.SO_KEEPALIVE, true)
                .childHandler(new HelixChannelInitializer(config, cacheService, storageExecutor));

        InetSocketAddress bind = config.bindAddress();
        ChannelFuture bindFuture = bootstrap.bind(bind).sync();
        serverChannel = bindFuture.channel();
        log.info("Helix {} listening on {}:{}", HelixConfig.VERSION, bind.getHostString(), bind.getPort());
    }

    public int port() {
        if (serverChannel == null) {
            return config.port();
        }
        return ((InetSocketAddress) serverChannel.localAddress()).getPort();
    }

    public void awaitShutdown() throws InterruptedException {
        if (serverChannel != null) {
            serverChannel.closeFuture().sync();
        }
    }

    @Override
    public void close() {
        if (serverChannel != null) {
            serverChannel.close().syncUninterruptibly();
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully().syncUninterruptibly();
        }
        if (bossGroup != null) {
            bossGroup.shutdownGracefully().syncUninterruptibly();
        }
        if (ownsExecutor) {
            storageExecutor.shutdown();
            try {
                storageExecutor.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        log.info("Helix server stopped");
    }
}
