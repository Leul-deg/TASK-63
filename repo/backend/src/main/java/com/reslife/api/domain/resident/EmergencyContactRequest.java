package com.reslife.api.domain.resident;

import jakarta.validation.constraints.*;

/**
 * Input for creating or updating an {@link EmergencyContact}.
 */
public record EmergencyContactRequest(

        @NotBlank(message = "Contact name is required")
        String name,

        @NotBlank(message = "Relationship is required")
        String relationship,

        @NotBlank(message = "Phone number is required")
        @Pattern(
                regexp = "^\\d{3}-\\d{3}-\\d{4}$",
                message = "Phone must use format 555-123-4567"
        )
        String phone,

        @Email(message = "Email must be a valid address")
        String email,

        boolean primary

) {}
