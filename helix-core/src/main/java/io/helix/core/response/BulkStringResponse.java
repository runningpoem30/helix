package io.helix.core.response;

import java.nio.charset.StandardCharsets;

/** Alias-style bulk response for INFO and similar multi-line payloads. */
public record BulkStringResponse(byte[] payload) implements Response {

    public static BulkStringResponse of(String text) {
        return new BulkStringResponse(text.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public byte[] encode() {
        String header = "$" + payload.length + "\r\n";
        byte[] headerBytes = header.getBytes(StandardCharsets.UTF_8);
        byte[] out = new byte[headerBytes.length + payload.length + 2];
        System.arraycopy(headerBytes, 0, out, 0, headerBytes.length);
        System.arraycopy(payload, 0, out, headerBytes.length, payload.length);
        out[out.length - 2] = '\r';
        out[out.length - 1] = '\n';
        return out;
    }
}
