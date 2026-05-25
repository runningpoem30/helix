package io.helix.network;

import io.helix.core.HelixConfig;
import io.helix.metrics.CacheMetrics;
import io.helix.storage.CacheEngine;
import io.helix.storage.CacheService;
import io.helix.storage.SegmentedCacheEngine;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

class HelixPipelineTest {

    @Test
    void setAndGetOverEmbeddedChannel() {
        HelixConfig config = HelixConfig.forTest(0);
        CacheEngine engine = new SegmentedCacheEngine(config);
        CacheService service = new CacheService(engine, new CacheMetrics());

        EmbeddedChannel channel = new EmbeddedChannel(
                new HelixChannelInitializer(config, service, DirectExecutorService.create()));

        channel.writeInbound(Unpooled.copiedBuffer("SET user:1 Arya\n", StandardCharsets.UTF_8));
        Object setResponse = channel.readOutbound();
        assertTrue(responseContains(setResponse, "+OK"));

        channel.writeInbound(Unpooled.copiedBuffer("GET user:1\n", StandardCharsets.UTF_8));
        Object getResponse = channel.readOutbound();
        assertTrue(responseContains(getResponse, "Arya"));
    }

    private static boolean responseContains(Object response, String text) {
        if (response instanceof ByteBuf buf) {
            String s = buf.toString(StandardCharsets.UTF_8);
            buf.release();
            return s.contains(text);
        }
        return false;
    }
}
