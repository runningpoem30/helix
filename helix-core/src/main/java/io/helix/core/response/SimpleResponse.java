package io.helix.core.response;

import java.nio.charset.StandardCharsets;

public record SimpleResponse(String message) implements Response {

    public static final SimpleResponse OK = new SimpleResponse("OK");
    public static final SimpleResponse PONG = new SimpleResponse("PONG");

    @Override
    public byte[] encode() {
        return ("+" + message + "\r\n").getBytes(StandardCharsets.UTF_8);
    }
}
