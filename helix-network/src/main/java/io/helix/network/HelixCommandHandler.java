package io.helix.network;

import io.helix.core.protocol.CommandParser.ParseResult;
import io.helix.core.response.CloseConnectionResponse;
import io.helix.core.response.Response;
import io.helix.storage.CacheService;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class HelixCommandHandler extends SimpleChannelInboundHandler<ParseResult> {

    private static final Logger log = LoggerFactory.getLogger(HelixCommandHandler.class);

    private final CacheService cacheService;

    public HelixCommandHandler(CacheService cacheService) {
        this.cacheService = cacheService;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ParseResult result) {
        Response response;
        if (result.error().isPresent()) {
            response = result.error().get();
        } else if (result.command().isPresent()) {
            response = cacheService.execute(result.command().get());
        } else {
            return;
        }

        ctx.writeAndFlush(response).addListener(future -> {
            if (response instanceof CloseConnectionResponse) {
                ctx.close();
            }
        });
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.warn("Connection error: {}", cause.getMessage());
        ctx.close();
    }
}
