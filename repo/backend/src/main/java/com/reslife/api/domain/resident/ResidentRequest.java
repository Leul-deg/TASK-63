package com.reslife.api.domain.resident;

import jakarta.validation.constraints.*;

import java.time.LocalDate;

/**
 * Input for creating or fully updating a {@link Resident}.
 *
 * <p>All write operations go through this DTO so that validation rules
 * live in one place and entity fields are never set directly from raw
 * request data.
 */
public record ResidentRequest(

        @NotBlank(message = "First name is required")
        @Size(max = 100, message = "First name must be 100 characters or fewer")
        String firstName,

        @NotBlank(message = "Last name is required")
        @Size(max = 100, message = "Last name must be 100 characters or fewer")
        String lastName,

        @NotBlank(message = "Email is required")
        @Email(message = "Email must be a valid address")
        @Size(max = 255)
        String email,

        /**
         * Optional. When provided must be {@code NNN-NNN-NNNN} (e.g. {@code 555-123-4567}).
         * {@code @Pattern} skips validation on null/blank, so the field remains truly optional.
         */
        @Pattern(
                regexp = "^\\d{3}-\\d{3}-\\d{4}$",
                message = "Phone must use format 555-123-4567"
        )
        String phone,

        @Size(max = 50, message = "Student ID must be 50 characters or fewer")
        String studentId,

        @Past(message = "Date of birth must be in the past")
        LocalDate dateOfBirth,

        @Size(max = 50, message = "Enrollment status must be 50 characters or fewer")
        String enrollmentStatus,

        @Size(max = 100, message = "Department must be 100 characters or fewer")
        String department,

        Integer classYear,

        @Size(max = 20, message = "Room number must be 20 characters or fewer")
        String roomNumber,

        @Size(max = 100, message = "Building name must be 100 characters or fewer")
        String buildingName

) {}
