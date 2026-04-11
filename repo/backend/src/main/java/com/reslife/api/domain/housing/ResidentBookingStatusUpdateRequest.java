package com.reslife.api.domain.housing;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ResidentBookingStatusUpdateRequest(
        @NotNull(message = "Booking status is required")
        ResidentBookingStatus status,

        @Size(max = 500, message = "Reason must be 500 characters or fewer")
        String reason
) {}
