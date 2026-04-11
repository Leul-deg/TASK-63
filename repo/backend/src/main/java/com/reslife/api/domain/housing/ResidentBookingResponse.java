package com.reslife.api.domain.housing;

import java.time.LocalDate;
import java.util.UUID;

public record ResidentBookingResponse(
        UUID id,
        UUID residentId,
        LocalDate requestedDate,
        String buildingName,
        String roomNumber,
        ResidentBookingStatus status,
        String purpose,
        String notes,
        String decisionReason,
        UUID createdByUserId
) {
    public static ResidentBookingResponse from(ResidentBooking booking) {
        return new ResidentBookingResponse(
                booking.getId(),
                booking.getResident().getId(),
                booking.getRequestedDate(),
                booking.getBuildingName(),
                booking.getRoomNumber(),
                booking.getStatus(),
                booking.getPurpose(),
                booking.getNotes(),
                booking.getDecisionReason(),
                booking.getCreatedBy() != null ? booking.getCreatedBy().getId() : null
        );
    }
}
