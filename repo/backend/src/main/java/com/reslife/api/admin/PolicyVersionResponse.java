package com.reslife.api.admin;

import com.reslife.api.domain.system.ConfigurationVersion;

import java.time.Instant;
import java.util.UUID;

/**
 * Wire representation of one versioned booking-policy snapshot.
 * Used in both the "current effective" and "history" responses.
 */
public record PolicyVersionResponse(
        UUID         id,
        int          version,
        boolean      active,
        String       description,
        String       createdByUsername,
        Instant      createdAt,
        BookingPolicy policy
) {
    public static PolicyVersionResponse from(ConfigurationVersion cv, BookingPolicy policy) {
        String author = cv.getCreatedBy() != null ? cv.getCreatedBy().getUsername() : null;
        return new PolicyVersionResponse(
                cv.getId(),
                cv.getVersion(),
                cv.isActive(),
                cv.getDescription(),
                author,
                cv.getCreatedAt(),
                policy
        );
    }
}
