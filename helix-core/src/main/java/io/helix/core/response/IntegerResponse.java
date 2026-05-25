package io.helix.core.response;

import java.nio.charset.StandardCharsets;

public record IntegerResponse(long value) implements Response {

    @Override
    public byte[] encode() {
        return (":" + value + "\r\n").getBytes(StandardCharsets.UTF_8);
    }
}
