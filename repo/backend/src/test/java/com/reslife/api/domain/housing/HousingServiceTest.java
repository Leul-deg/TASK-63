package com.reslife.api.domain.housing;

import com.reslife.api.domain.resident.Resident;
import com.reslife.api.domain.resident.ResidentService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link HousingService} business logic.
 * All collaborators are mocked — no Spring context is loaded.
 */
class HousingServiceTest {

    private final MoveInRecordRepository      moveInRecordRepository  = mock(MoveInRecordRepository.class);
    private final HousingAgreementRepository  housingAgreementRepository = mock(HousingAgreementRepository.class);
    private final ResidentService             residentService         = mock(ResidentService.class);

    private HousingService housingService;

    private static final UUID RESIDENT_ID  = UUID.randomUUID();
    private static final UUID RECORD_ID    = UUID.randomUUID();
    private static final UUID AGREEMENT_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        housingService = new HousingService(
                moveInRecordRepository, housingAgreementRepository, residentService);
    }

    // ── createMoveInRecord ────────────────────────────────────────────────────

    @Test
    void createMoveInRecord_linksResidentAndSyncsRoomOnResident() {
        Resident resident = mock(Resident.class);
        when(residentService.findById(RESIDENT_ID)).thenReturn(resident);

        MoveInRecord input = new MoveInRecord();
        input.setRoomNumber("101");
        input.setBuildingName("Main Hall");
        input.setMoveInDate(LocalDate.now());
        input.setCheckInStatus(CheckInStatus.PENDING);

        MoveInRecord saved = new MoveInRecord();
        saved.setRoomNumber("101");
        saved.setBuildingName("Main Hall");
        when(moveInRecordRepository.save(input)).thenReturn(saved);

        MoveInRecord result = housingService.createMoveInRecord(RESIDENT_ID, input);

        assertThat(result).isEqualTo(saved);
        verify(resident).setRoomNumber("101");
        verify(resident).setBuildingName("Main Hall");
        verify(residentService).save(resident);
    }

    // ── updateCheckInStatus ───────────────────────────────────────────────────

    @Test
    void updateCheckInStatus_updatesAndSaves() {
        MoveInRecord record = new MoveInRecord();
        record.setCheckInStatus(CheckInStatus.PENDING);

        when(moveInRecordRepository.findById(RECORD_ID)).thenReturn(Optional.of(record));
        when(moveInRecordRepository.save(record)).thenReturn(record);

        MoveInRecord result = housingService.updateCheckInStatus(RECORD_ID, CheckInStatus.CHECKED_IN);

        assertThat(result.getCheckInStatus()).isEqualTo(CheckInStatus.CHECKED_IN);
        verify(moveInRecordRepository).save(record);
    }

    @Test
    void updateCheckInStatus_throwsWhenRecordNotFound() {
        when(moveInRecordRepository.findById(RECORD_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> housingService.updateCheckInStatus(RECORD_ID, CheckInStatus.CHECKED_IN))
                .isInstanceOf(EntityNotFoundException.class);
    }

    // ── createAgreement ───────────────────────────────────────────────────────

    @Test
    void createAgreement_setsResidentOnAgreement() {
        Resident resident = mock(Resident.class);
        when(residentService.findById(RESIDENT_ID)).thenReturn(resident);

        HousingAgreement input = new HousingAgreement();
        when(housingAgreementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        HousingAgreement result = housingService.createAgreement(RESIDENT_ID, input);

        assertThat(result.getResident()).isSameAs(resident);
        verify(housingAgreementRepository).save(input);
    }

    // ── updateAgreementStatus ─────────────────────────────────────────────────

    @Test
    void updateAgreementStatus_updatesAndSaves() {
        HousingAgreement agreement = new HousingAgreement();
        agreement.setStatus(AgreementStatus.PENDING);

        when(housingAgreementRepository.findById(AGREEMENT_ID)).thenReturn(Optional.of(agreement));
        when(housingAgreementRepository.save(agreement)).thenReturn(agreement);

        HousingAgreement result = housingService.updateAgreementStatus(AGREEMENT_ID, AgreementStatus.SIGNED);

        assertThat(result.getStatus()).isEqualTo(AgreementStatus.SIGNED);
        verify(housingAgreementRepository).save(agreement);
    }

    // ── findAgreementById — not found ─────────────────────────────────────────

    @Test
    void findAgreementById_throwsWhenNotFound() {
        when(housingAgreementRepository.findById(AGREEMENT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> housingService.findAgreementById(AGREEMENT_ID))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining(AGREEMENT_ID.toString());
    }
}
