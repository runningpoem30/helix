package io.helix.network;

import io.helix.core.HelixConfig;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

import java.nio.charset.StandardCharsets;

final class WelcomeHandler extends ChannelInboundHandlerAdapter {

    private final boolean sendBanner;

    WelcomeHandler(boolean sendBanner) {
        this.sendBanner = sendBanner;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        if (sendBanner) {
            ctx.writeAndFlush(ctx.alloc().buffer().writeBytes(
                    ("# Helix " + HelixConfig.VERSION + "\r\n").getBytes(StandardCharsets.UTF_8)));
        }
        super.channelActive(ctx);
    }
}
