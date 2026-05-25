package io.helix.network;

import io.helix.core.protocol.CommandParser.ParseResult;
import io.helix.core.response.CloseConnectionResponse;
import io.helix.core.response.Response;
import io.helix.storage.CacheService;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;

public final class HelixCommandHandler extends SimpleChannelInboundHandler<ParseResult> {

    private static final Logger log = LoggerFactory.getLogger(HelixCommandHandler.class);

    private final CacheService cacheService;
    private final ExecutorService storageExecutor;

    public HelixCommandHandler(CacheService cacheService, ExecutorService storageExecutor) {
        this.cacheService = cacheService;
        this.storageExecutor = storageExecutor;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ParseResult result) {
        Runnable work = () -> {
            Response response = resolve(result);
            if (response == null) {
                return;
            }
            Runnable write = () -> ctx.writeAndFlush(response).addListener(future -> {
                if (response instanceof CloseConnectionResponse) {
                    ctx.close();
                }
            });
            if (ctx.channel().eventLoop().inEventLoop()) {
                write.run();
            } else {
                ctx.channel().eventLoop().execute(write);
            }
        };
        storageExecutor.execute(work);
    }

    private Response resolve(ParseResult result) {
        if (result.error().isPresent()) {
            return result.error().get();
        }
        if (result.command().isPresent()) {
            return cacheService.execute(result.command().get());
        }
        return null;
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.warn("Connection error: {}", cause.getMessage());
        ctx.close();
    }
}
