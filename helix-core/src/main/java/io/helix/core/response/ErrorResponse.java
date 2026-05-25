package io.helix.core.response;

import java.nio.charset.StandardCharsets;

public record ErrorResponse(String message) implements Response {

    public static ErrorResponse syntax() {
        return new ErrorResponse("syntax error");
    }

    public static ErrorResponse unknownCommand(String name) {
        return new ErrorResponse("unknown command '" + name + "'");
    }

    public static ErrorResponse keyTooLong() {
        return new ErrorResponse("key too long");
    }

    public static ErrorResponse valueTooLarge() {
        return new ErrorResponse("value too large");
    }

    @Override
    public byte[] encode() {
        return ("-ERR " + message + "\r\n").getBytes(StandardCharsets.UTF_8);
    }
}
