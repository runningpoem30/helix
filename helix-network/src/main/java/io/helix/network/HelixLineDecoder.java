package io.helix.network;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Accumulates bytes until a newline and emits one UTF-8 line (without line ending).
 */
public final class HelixLineDecoder extends ByteToMessageDecoder {

    private final int maxLineLength;

    public HelixLineDecoder(int maxLineLength) {
        this.maxLineLength = maxLineLength;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        while (true) {
            int readable = in.readableBytes();
            if (readable == 0) {
                return;
            }

            int lineEnd = indexOfNewline(in);
            if (lineEnd < 0) {
                if (readable > maxLineLength) {
                    in.skipBytes(readable);
                    ctx.close();
                }
                return;
            }

            int lineLength = lineEnd - in.readerIndex();
            if (lineLength > maxLineLength) {
                in.readerIndex(lineEnd);
                skipLineEnding(in);
                ctx.close();
                return;
            }

            byte[] lineBytes = new byte[lineLength];
            in.readBytes(lineBytes);
            skipLineEnding(in);

            String line = new String(lineBytes, StandardCharsets.UTF_8).trim();
            if (!line.isEmpty()) {
                out.add(line);
            }
        }
    }

    private static int indexOfNewline(ByteBuf in) {
        int start = in.readerIndex();
        int end = start + in.readableBytes();
        for (int i = start; i < end; i++) {
            byte b = in.getByte(i);
            if (b == '\n' || b == '\r') {
                return i;
            }
        }
        return -1;
    }

    private static void skipLineEnding(ByteBuf in) {
        if (in.isReadable() && in.getByte(in.readerIndex()) == '\r') {
            in.readByte();
        }
        if (in.isReadable() && in.getByte(in.readerIndex()) == '\n') {
            in.readByte();
        }
    }
}
