package com.reslife.api.domain.housing;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MoveInRecordRepository extends JpaRepository<MoveInRecord, UUID> {
    List<MoveInRecord> findByResidentId(UUID residentId);
    List<MoveInRecord> findByBuildingName(String buildingName);
    List<MoveInRecord> findByCheckInStatus(CheckInStatus status);

    Optional<MoveInRecord> findTopByResidentIdOrderByMoveInDateDesc(UUID residentId);

    long countByResidentIdAndCheckInStatusAndMoveInDateGreaterThanEqual(
            UUID residentId, CheckInStatus status, LocalDate moveInDate);
}
