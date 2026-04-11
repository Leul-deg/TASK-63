package com.reslife.api.domain.housing;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Safe API representation of a {@link MoveInRecord}.
 */
public record MoveInRecordResponse(
        UUID        id,
        String      roomNumber,
        String      buildingName,
        LocalDate   moveInDate,
        LocalDate   moveOutDate,
        CheckInStatus checkInStatus,
        String      notes
) {
    public static MoveInRecordResponse from(MoveInRecord r) {
        return new MoveInRecordResponse(
                r.getId(),
                r.getRoomNumber(),
                r.getBuildingName(),
                r.getMoveInDate(),
                r.getMoveOutDate(),
                r.getCheckInStatus(),
                r.getNotes()
        );
    }
}
