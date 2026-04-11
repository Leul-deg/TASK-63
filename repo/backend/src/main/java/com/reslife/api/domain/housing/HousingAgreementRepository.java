package com.reslife.api.domain.housing;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface HousingAgreementRepository extends JpaRepository<HousingAgreement, UUID> {
    List<HousingAgreement> findByResidentId(UUID residentId);
    List<HousingAgreement> findByResidentIdAndStatus(UUID residentId, AgreementStatus status);
    List<HousingAgreement> findByStatus(AgreementStatus status);
}
