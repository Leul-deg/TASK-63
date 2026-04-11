package com.reslife.api.domain.integration;

import java.time.Instant;
import java.util.UUID;

/**
 * Returned at key creation time only.  The {@code secret} is the plaintext
 * HMAC secret — it is shown exactly once and cannot be retrieved again.
 * Store it securely immediately after creation.
 */
public record CreateKeyResponse(
        UUID    id,
        String  keyId,
        String  name,
        String  description,
        String  secret,         // plaintext — shown once, never stored in plaintext
        String  secretPrefix,
        String  allowedEvents,
        boolean active,
        Instant createdAt
) {}
