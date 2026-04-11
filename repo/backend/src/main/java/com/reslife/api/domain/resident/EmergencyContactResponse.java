package com.reslife.api.domain.resident;

import com.reslife.api.encryption.SensitiveAccessLevel;

import java.util.UUID;

/**
 * Safe API representation of an {@link EmergencyContact}.
 *
 * <p>All personal fields ({@code name}, {@code phone}, {@code email},
 * {@code relationship}) are {@code null} when the caller's access level is
 * {@link SensitiveAccessLevel#NONE}. The {@code isPrimary} flag is always
 * included so the frontend can indicate which contact is the primary one
 * without leaking contact details.
 */
public record EmergencyContactResponse(
        UUID    id,
        /** {@code null} when access level is NONE. */
        String  name,
        /** {@code null} when access level is NONE. */
        String  relationship,
        /** {@code null} when access level is NONE. */
        String  phone,
        /** {@code null} when access level is NONE. */
        String  email,
        boolean primary
) {
    public static EmergencyContactResponse from(EmergencyContact c, SensitiveAccessLevel level) {
        boolean full = level == SensitiveAccessLevel.FULL;
        return new EmergencyContactResponse(
                c.getId(),
                full ? c.getName()         : null,
                full ? c.getRelationship() : null,
                full ? c.getPhone()        : null,
                full ? c.getEmail()        : null,
                c.isPrimary()
        );
    }
}
