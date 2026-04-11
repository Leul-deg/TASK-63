package com.reslife.api.domain.housing;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ResidentBookingRepository extends JpaRepository<ResidentBooking, UUID> {

    List<ResidentBooking> findByResidentIdOrderByRequestedDateDescCreatedAtDesc(UUID residentId);

    boolean existsByResidentIdAndRequestedDateAndBuildingNameIgnoreCaseAndStatusIn(
            UUID residentId,
            LocalDate requestedDate,
            String buildingName,
            Collection<ResidentBookingStatus> statuses
    );
}
