package com.reslife.api.domain.messaging;

/**
 * Thrown when a staff user attempts to start a thread with a student who has blocked them.
 * Maps to HTTP 403 in {@code GlobalExceptionHandler}.
 */
public class BlockedException extends RuntimeException {
    public BlockedException(String message) {
        super(message);
    }
}
