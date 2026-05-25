package io.helix.core.response;

/**
 * Server response encoded as RESP-inspired wire format.
 */
public sealed interface Response
        permits SimpleResponse, ErrorResponse, BulkResponse, BulkStringResponse, IntegerResponse,
                CloseConnectionResponse {
    byte[] encode();
}
