package com.reslife.api.domain.integration;

import java.time.Instant;
import java.util.UUID;

/** Safe representation of a webhook endpoint — never includes the signing secret. */
public record WebhookResponse(
        UUID    id,
        String  name,
        String  targetUrl,
        String  eventTypes,
        String  signingSecretPrefix,
        boolean active,
        Instant createdAt
) {
    public static WebhookResponse from(WebhookEndpoint w) {
        return new WebhookResponse(
                w.getId(),
                w.getName(),
                w.getTargetUrl(),
                w.getEventTypes(),
                w.getSigningSecretPrefix(),
                w.isActive(),
                w.getCreatedAt()
        );
    }
}
