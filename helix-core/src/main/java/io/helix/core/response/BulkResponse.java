package io.helix.core.response;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

public record BulkResponse(Optional<byte[]> value) implements Response {

    public static final BulkResponse NIL = new BulkResponse(Optional.empty());

    public static BulkResponse of(byte[] bytes) {
        return new BulkResponse(Optional.of(bytes));
    }

    @Override
    public byte[] encode() {
        if (value.isEmpty()) {
            return "$-1\r\n".getBytes(StandardCharsets.UTF_8);
        }
        byte[] v = value.get();
        String header = "$" + v.length + "\r\n";
        byte[] headerBytes = header.getBytes(StandardCharsets.UTF_8);
        byte[] out = new byte[headerBytes.length + v.length + 2];
        System.arraycopy(headerBytes, 0, out, 0, headerBytes.length);
        System.arraycopy(v, 0, out, headerBytes.length, v.length);
        out[out.length - 2] = '\r';
        out[out.length - 1] = '\n';
        return out;
    }
}
