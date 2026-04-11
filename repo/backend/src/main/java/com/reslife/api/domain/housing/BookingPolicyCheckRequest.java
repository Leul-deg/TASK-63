package com.reslife.api.domain.housing;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Input for evaluating whether a proposed booking date is allowed under the
 * current booking/visit policy.
 */
public record BookingPolicyCheckRequest(
        @NotNull(message = "Requested date is required")
        LocalDate requestedDate,

        @Size(max = 100, message = "Building name must be 100 characters or fewer")
        String buildingName
) {}
