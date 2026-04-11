package com.reslife.api.domain.resident;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface EmergencyContactRepository extends JpaRepository<EmergencyContact, UUID> {
    List<EmergencyContact> findByResidentId(UUID residentId);
    List<EmergencyContact> findByResidentIdAndPrimaryTrue(UUID residentId);
}
