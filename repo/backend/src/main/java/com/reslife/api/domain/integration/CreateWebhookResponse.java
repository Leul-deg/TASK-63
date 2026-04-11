package com.reslife.api.domain.integration;

import java.time.Instant;
import java.util.UUID;

/**
 * Returned at webhook endpoint creation time only.  The {@code signingSecret} is
 * the plaintext signing secret — shown once and not retrievable afterwards.
 * The receiving system uses it to verify the {@code X-Reslife-Signature} header.
 */
public record CreateWebhookResponse(
        UUID    id,
        String  name,
        String  targetUrl,
        String  eventTypes,
        String  signingSecret,       // plaintext — shown once only
        String  signingSecretPrefix,
        boolean active,
        Instant createdAt
) {}
