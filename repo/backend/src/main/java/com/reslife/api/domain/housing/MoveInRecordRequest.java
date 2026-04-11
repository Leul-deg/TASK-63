package com.reslife.api.domain.housing;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Input for creating or updating a {@link MoveInRecord}.
 */
public record MoveInRecordRequest(

        @NotBlank(message = "Room number is required")
        @Size(max = 20, message = "Room number must be 20 characters or fewer")
        String roomNumber,

        @NotBlank(message = "Building name is required")
        @Size(max = 100, message = "Building name must be 100 characters or fewer")
        String buildingName,

        @NotNull(message = "Move-in date is required")
        LocalDate moveInDate,

        LocalDate moveOutDate,

        CheckInStatus checkInStatus,

        String notes

) {}
