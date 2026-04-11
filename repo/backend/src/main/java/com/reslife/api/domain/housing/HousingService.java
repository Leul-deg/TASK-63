package com.reslife.api.domain.housing;

import com.reslife.api.domain.resident.Resident;
import com.reslife.api.domain.resident.ResidentService;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class HousingService {

    private final MoveInRecordRepository moveInRecordRepository;
    private final HousingAgreementRepository housingAgreementRepository;
    private final ResidentService residentService;

    public HousingService(MoveInRecordRepository moveInRecordRepository,
                          HousingAgreementRepository housingAgreementRepository,
                          ResidentService residentService) {
        this.moveInRecordRepository = moveInRecordRepository;
        this.housingAgreementRepository = housingAgreementRepository;
        this.residentService = residentService;
    }

    // --- Move-In Records ---

    public MoveInRecord findMoveInRecordById(UUID id) {
        return moveInRecordRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("MoveInRecord not found: " + id));
    }

    public List<MoveInRecord> findMoveInRecordsByResident(UUID residentId) {
        return moveInRecordRepository.findByResidentId(residentId);
    }

    @Transactional
    public MoveInRecord createMoveInRecord(UUID residentId, MoveInRecord record) {
        Resident resident = residentService.findById(residentId);
        record.setResident(resident);
        MoveInRecord saved = moveInRecordRepository.save(record);
        // Sync room assignment on resident
        resident.setRoomNumber(record.getRoomNumber());
        resident.setBuildingName(record.getBuildingName());
        residentService.save(resident);
        return saved;
    }

    @Transactional
    public MoveInRecord updateCheckInStatus(UUID recordId, CheckInStatus status) {
        MoveInRecord record = findMoveInRecordById(recordId);
        record.setCheckInStatus(status);
        return moveInRecordRepository.save(record);
    }

    // --- Housing Agreements ---

    public HousingAgreement findAgreementById(UUID id) {
        return housingAgreementRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("HousingAgreement not found: " + id));
    }

    public List<HousingAgreement> findAgreementsByResident(UUID residentId) {
        return housingAgreementRepository.findByResidentId(residentId);
    }

    @Transactional
    public HousingAgreement createAgreement(UUID residentId, HousingAgreement agreement) {
        Resident resident = residentService.findById(residentId);
        agreement.setResident(resident);
        return housingAgreementRepository.save(agreement);
    }

    @Transactional
    public HousingAgreement updateAgreementStatus(UUID agreementId, AgreementStatus status) {
        HousingAgreement agreement = findAgreementById(agreementId);
        agreement.setStatus(status);
        return housingAgreementRepository.save(agreement);
    }
}
