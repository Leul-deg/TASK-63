package com.reslife.api.domain.integration;

/**
 * Thrown when an inbound request's HMAC signature fails verification,
 * or when the timestamp is outside the 5-minute replay window.
 */
public class InvalidSignatureException extends RuntimeException {
    public InvalidSignatureException(String message) {
        super(message);
    }
}
