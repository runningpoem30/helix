package io.helix.core.response;

/**
 * Server response encoded as RESP-inspired wire format.
 */
public sealed interface Response
        permits SimpleResponse, ErrorResponse, BulkResponse, IntegerResponse, CloseConnectionResponse {

    byte[] encode();
}
