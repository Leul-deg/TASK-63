package com.reslife.api.domain.housing;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record ResidentBookingRequest(
        @NotNull(message = "Requested date is required")
        @FutureOrPresent(message = "Requested date must be today or later")
        LocalDate requestedDate,

        @NotBlank(message = "Building name is required")
        @Size(max = 100, message = "Building name must be 100 characters or fewer")
        String buildingName,

        @Size(max = 20, message = "Room number must be 20 characters or fewer")
        String roomNumber,

        @Size(max = 255, message = "Purpose must be 255 characters or fewer")
        String purpose,

        String notes
) {}
