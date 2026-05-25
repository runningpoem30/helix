package io.helix.network;

import io.helix.core.protocol.CommandParser;
import io.helix.core.protocol.CommandParser.ParseResult;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;

import java.util.List;

/**
 * Converts a line string into a {@link ParseResult} for the command handler.
 */
public final class HelixCommandDecoder extends MessageToMessageDecoder<String> {

    private final CommandParser parser;

    public HelixCommandDecoder(CommandParser parser) {
        this.parser = parser;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, String line, List<Object> out) {
        ParseResult result = parser.parse(line);
        if (!result.isEmpty()) {
            out.add(result);
        }
    }
}
