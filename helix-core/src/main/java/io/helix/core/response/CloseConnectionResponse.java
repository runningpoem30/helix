package io.helix.core.response;

/**
 * Signals the network layer to close the channel after optional final bytes are sent.
 */
public record CloseConnectionResponse(Response farewell) implements Response {

    public CloseConnectionResponse() {
        this(SimpleResponse.OK);
    }

    @Override
    public byte[] encode() {
        return farewell.encode();
    }
}
