package com.reslife.api.domain.resident;

import com.reslife.api.encryption.SensitiveAccessLevel;

import java.util.UUID;

/**
 * Safe API representation of a {@link Resident}.
 *
 * <p>The {@code dateOfBirth} field is only populated when the caller holds
 * a role with {@link SensitiveAccessLevel#FULL} access. Otherwise it is
 * {@code null}, indicating the field is restricted.
 *
 * <p>The frontend applies a second, UX-layer of masking on top of this:
 * staff see the real value masked with bullets by default, with a per-field
 * "Reveal" button. Standard resident-directory responses return {@code null}
 * for students, while the dedicated student self-service endpoint may expose
 * the student's own values explicitly.
 */
public record ResidentResponse(
        UUID    id,
        String  studentId,
        String  firstName,
        String  lastName,
        String  email,
        String  phone,
        /** ISO-8601 date string, or {@code null} when access level is NONE. */
        String  dateOfBirth,
        String  enrollmentStatus,
        String  department,
        String  roomNumber,
        String  buildingName,
        Integer classYear
) {
    public static ResidentResponse from(Resident r, SensitiveAccessLevel level) {
        return new ResidentResponse(
                r.getId(),
                r.getStudentId(),
                r.getFirstName(),
                r.getLastName(),
                r.getEmail(),
                r.getPhone(),
                level == SensitiveAccessLevel.FULL && r.getDateOfBirth() != null
                        ? r.getDateOfBirth().toString()
                        : null,
                r.getEnrollmentStatus(),
                r.getDepartment(),
                r.getRoomNumber(),
                r.getBuildingName(),
                r.getClassYear()
        );
    }
}
