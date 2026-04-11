package com.reslife.api.domain.integration;

import java.time.Instant;
import java.util.UUID;

/** Safe representation of an integration key — never includes the secret. */
public record KeyResponse(
        UUID    id,
        String  keyId,
        String  name,
        String  description,
        String  secretPrefix,
        String  allowedEvents,
        boolean active,
        Instant lastUsedAt,
        Instant revokedAt,
        String  revokedReason,
        String  createdByUsername,
        Instant createdAt
) {
    public static KeyResponse from(IntegrationKey k) {
        return new KeyResponse(
                k.getId(),
                k.getKeyId(),
                k.getName(),
                k.getDescription(),
                k.getSecretPrefix(),
                k.getAllowedEvents(),
                k.isActive(),
                k.getLastUsedAt(),
                k.getRevokedAt(),
                k.getRevokedReason(),
                k.getCreatedBy() != null ? k.getCreatedBy().getUsername() : null,
                k.getCreatedAt()
        );
    }
}
