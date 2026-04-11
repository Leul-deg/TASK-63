package com.reslife.api.domain.integration;

/**
 * Thrown when an integration key exceeds its request rate limit
 * (60 requests / 60-second sliding window).
 */
public class RateLimitExceededException extends RuntimeException {
    public RateLimitExceededException(String keyId) {
        super("Rate limit exceeded for integration key: " + keyId);
    }
}
