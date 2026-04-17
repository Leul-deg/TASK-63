package com.reslife.api.domain.housing;

import com.reslife.api.domain.resident.Resident;
import com.reslife.api.domain.resident.ResidentRepository;
import com.reslife.api.encryption.EncryptionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JPA-layer integration tests for {@link MoveInRecordRepository}.
 *
 * <p>These queries are the backbone of {@link BookingPolicyEnforcementService}:
 * {@code findTopByResidentIdOrderByMoveInDateDesc} returns the most recent
 * assignment and {@code countByResidentIdAndCheckInStatusAndMoveInDateGreaterThanEqual}
 * drives the no-show threshold check.  Verifying them at the SQL level catches
 * any field mapping or ORDER BY mistake that unit tests cannot detect.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:reslife_movein;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DEFAULT_NULL_ORDERING=HIGH",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "app.encryption.key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
    "spring.session.store-type=none",
    "spring.jpa.show-sql=false"
})
@Import(EncryptionService.class)
class MoveInRecordRepositoryTest {

    @Autowired private MoveInRecordRepository moveInRepo;
    @Autowired private ResidentRepository     residentRepo;
    @Autowired private TestEntityManager      em;

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Resident savedResident(String email) {
        Resident r = new Resident();
        r.setFirstName("Test");
        r.setLastName("Resident");
        r.setEmail(email);
        return em.persistAndFlush(r);
    }

    private MoveInRecord record(Resident resident, LocalDate moveIn, CheckInStatus status) {
        MoveInRecord rec = new MoveInRecord();
        rec.setResident(resident);
        rec.setRoomNumber("101");
        rec.setBuildingName("Test Hall");
        rec.setMoveInDate(moveIn);
        rec.setCheckInStatus(status);
        return em.persistAndFlush(rec);
    }

    // ── findByResidentId ───────────────────────────────────────────────────────

    @Test
    void findByResidentId_returnsEmpty_whenNoRecordsExist() {
        Resident r = savedResident("nobody@test.com");
        assertThat(moveInRepo.findByResidentId(r.getId())).isEmpty();
    }

    @Test
    void findByResidentId_returnsAllRecordsForThatResident() {
        Resident r = savedResident("multi@test.com");
        record(r, LocalDate.of(2025, 9, 1), CheckInStatus.CHECKED_IN);
        record(r, LocalDate.of(2026, 1, 15), CheckInStatus.PENDING);

        List<MoveInRecord> results = moveInRepo.findByResidentId(r.getId());
        assertThat(results).hasSize(2);
    }

    @Test
    void findByResidentId_doesNotReturnRecordsForOtherResidents() {
        Resident r1 = savedResident("r1@test.com");
        Resident r2 = savedResident("r2@test.com");
        record(r1, LocalDate.of(2025, 9, 1), CheckInStatus.CHECKED_IN);

        assertThat(moveInRepo.findByResidentId(r2.getId())).isEmpty();
    }

    // ── findTopByResidentIdOrderByMoveInDateDesc ───────────────────────────────

    @Test
    void findTopByResidentIdOrderByMoveInDateDesc_returnsEmpty_whenNoRecords() {
        Resident r = savedResident("top-none@test.com");
        assertThat(moveInRepo.findTopByResidentIdOrderByMoveInDateDesc(r.getId())).isEmpty();
    }

    @Test
    void findTopByResidentIdOrderByMoveInDateDesc_returnsMostRecentRecord() {
        Resident r = savedResident("top-multi@test.com");
        record(r, LocalDate.of(2024, 8, 1), CheckInStatus.CHECKED_IN);
        record(r, LocalDate.of(2026, 1, 15), CheckInStatus.PENDING); // most recent

        Optional<MoveInRecord> top = moveInRepo.findTopByResidentIdOrderByMoveInDateDesc(r.getId());
        assertThat(top).isPresent();
        assertThat(top.get().getMoveInDate()).isEqualTo(LocalDate.of(2026, 1, 15));
    }

    // ── countByResidentIdAndCheckInStatusAndMoveInDateGreaterThanEqual ─────────

    @Test
    void countNoShows_returnsZero_whenNoRecentNoShows() {
        Resident r = savedResident("noshow-none@test.com");
        record(r, LocalDate.of(2026, 3, 1), CheckInStatus.CHECKED_IN);

        long count = moveInRepo.countByResidentIdAndCheckInStatusAndMoveInDateGreaterThanEqual(
                r.getId(), CheckInStatus.NO_SHOW, LocalDate.of(2026, 1, 1));
        assertThat(count).isZero();
    }

    @Test
    void countNoShows_countsOnlyNoShowsWithinWindow() {
        Resident r = savedResident("noshow-count@test.com");
        // Two recent no-shows within the window
        record(r, LocalDate.of(2026, 2, 10), CheckInStatus.NO_SHOW);
        record(r, LocalDate.of(2026, 3, 5),  CheckInStatus.NO_SHOW);
        // One old no-show outside the window
        record(r, LocalDate.of(2025, 6, 1),  CheckInStatus.NO_SHOW);
        // One checked-in (not a no-show)
        record(r, LocalDate.of(2026, 1, 20), CheckInStatus.CHECKED_IN);

        long count = moveInRepo.countByResidentIdAndCheckInStatusAndMoveInDateGreaterThanEqual(
                r.getId(), CheckInStatus.NO_SHOW, LocalDate.of(2026, 1, 1));
        assertThat(count).isEqualTo(2);
    }

    // ── findByCheckInStatus ────────────────────────────────────────────────────

    @Test
    void findByCheckInStatus_returnsMatchingRecordsAcrossResidents() {
        Resident r1 = savedResident("status-r1@test.com");
        Resident r2 = savedResident("status-r2@test.com");
        record(r1, LocalDate.of(2026, 3, 1),  CheckInStatus.NO_SHOW);
        record(r2, LocalDate.of(2026, 3, 10), CheckInStatus.CHECKED_IN);

        List<MoveInRecord> noShows = moveInRepo.findByCheckInStatus(CheckInStatus.NO_SHOW);
        assertThat(noShows).hasSize(1);
        assertThat(noShows.get(0).getResident().getId()).isEqualTo(r1.getId());
    }

    @TestConfiguration
    @EnableJpaAuditing
    static class AuditingConfig {}
}
