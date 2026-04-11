package com.reslife.api.admin;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code PUT /api/admin/booking-policy}.
 *
 * <p>Creates a new immutable version of the booking policy.
 * A human-readable {@code description} is strongly encouraged so that
 * operators can understand the version history at a glance.
 */
public record UpdateBookingPolicyRequest(

        @NotNull(message = "Policy is required")
        @Valid
        BookingPolicy policy,

        @Size(max = 500, message = "Description must be 500 characters or fewer")
        String description
) {}
