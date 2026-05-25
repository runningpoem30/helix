package io.helix.network;

import io.helix.core.HelixConfig;
import io.helix.core.protocol.CommandParser;
import io.helix.storage.CacheService;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.handler.timeout.IdleStateHandler;

import java.util.concurrent.TimeUnit;

public final class HelixChannelInitializer extends ChannelInitializer<Channel> {

    private final HelixConfig config;
    private final CacheService cacheService;
    private final CommandParser commandParser;

    public HelixChannelInitializer(HelixConfig config, CacheService cacheService) {
        this.config = config;
        this.cacheService = cacheService;
        this.commandParser = new CommandParser(config);
    }

    @Override
    protected void initChannel(Channel ch) {
        ch.pipeline()
                .addLast("welcome", new WelcomeHandler(config.sendBanner()))
                .addLast("idle", new IdleStateHandler(300, 0, 0, TimeUnit.SECONDS))
                .addLast("lineDecoder", new HelixLineDecoder(config.maxCommandBytes()))
                .addLast("commandDecoder", new HelixCommandDecoder(commandParser))
                .addLast("responseEncoder", new HelixResponseEncoder())
                .addLast("handler", new HelixCommandHandler(cacheService));
    }
}
