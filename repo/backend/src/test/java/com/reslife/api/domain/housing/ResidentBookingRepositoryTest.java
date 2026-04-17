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
import java.util.EnumSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JPA-layer tests for {@link ResidentBookingRepository}.
 *
 * <p>Validates the ordering query and the duplicate-detection predicate used
 * by {@link ResidentBookingService#createBooking} to prevent double-bookings.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:reslife_rb;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DEFAULT_NULL_ORDERING=HIGH",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "app.encryption.key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
    "spring.session.store-type=none",
    "spring.jpa.show-sql=false"
})
@Import(EncryptionService.class)
class ResidentBookingRepositoryTest {

    @Autowired private ResidentBookingRepository bookingRepo;
    @Autowired private ResidentRepository        residentRepo;
    @Autowired private TestEntityManager         em;

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Resident savedResident(String email) {
        Resident r = new Resident();
        r.setFirstName("Test");
        r.setLastName("Resident");
        r.setEmail(email);
        return em.persistAndFlush(r);
    }

    private ResidentBooking booking(Resident r, LocalDate date, String building,
                                    ResidentBookingStatus status) {
        ResidentBooking b = new ResidentBooking();
        b.setResident(r);
        b.setRequestedDate(date);
        b.setBuildingName(building);
        b.setStatus(status);
        return em.persistAndFlush(b);
    }

    // ── findByResidentIdOrderByRequestedDateDescCreatedAtDesc ─────────────────

    @Test
    void findByResident_returnsEmpty_whenNoBookings() {
        Resident r = savedResident("nobody-rb@test.com");
        assertThat(bookingRepo.findByResidentIdOrderByRequestedDateDescCreatedAtDesc(r.getId()))
                .isEmpty();
    }

    @Test
    void findByResident_returnsBookingsOrderedByDateDesc() {
        Resident r = savedResident("multi-rb@test.com");
        booking(r, LocalDate.of(2026, 1, 10), "Hall A", ResidentBookingStatus.REQUESTED);
        booking(r, LocalDate.of(2026, 4, 20), "Hall B", ResidentBookingStatus.CONFIRMED);
        booking(r, LocalDate.of(2026, 2, 15), "Hall C", ResidentBookingStatus.CANCELLED);

        List<ResidentBooking> results =
                bookingRepo.findByResidentIdOrderByRequestedDateDescCreatedAtDesc(r.getId());

        assertThat(results).hasSize(3);
        assertThat(results.get(0).getRequestedDate()).isEqualTo(LocalDate.of(2026, 4, 20));
        assertThat(results.get(1).getRequestedDate()).isEqualTo(LocalDate.of(2026, 2, 15));
        assertThat(results.get(2).getRequestedDate()).isEqualTo(LocalDate.of(2026, 1, 10));
    }

    @Test
    void findByResident_doesNotReturnOtherResidentsBookings() {
        Resident r1 = savedResident("r1-rb@test.com");
        Resident r2 = savedResident("r2-rb@test.com");
        booking(r1, LocalDate.of(2026, 5, 1), "Hall A", ResidentBookingStatus.REQUESTED);

        assertThat(bookingRepo.findByResidentIdOrderByRequestedDateDescCreatedAtDesc(r2.getId()))
                .isEmpty();
    }

    // ── existsByResidentIdAndRequestedDateAndBuildingNameIgnoreCaseAndStatusIn ──

    @Test
    void existsByDuplicate_returnsTrue_whenMatchingActiveBookingExists() {
        Resident r = savedResident("dup-rb@test.com");
        booking(r, LocalDate.of(2026, 6, 15), "Maple Hall", ResidentBookingStatus.REQUESTED);

        boolean exists = bookingRepo
                .existsByResidentIdAndRequestedDateAndBuildingNameIgnoreCaseAndStatusIn(
                        r.getId(),
                        LocalDate.of(2026, 6, 15),
                        "maple hall",   // case-insensitive match
                        EnumSet.of(ResidentBookingStatus.REQUESTED,
                                   ResidentBookingStatus.CONFIRMED));

        assertThat(exists).isTrue();
    }

    @Test
    void existsByDuplicate_returnsFalse_whenBookingStatusIsNotActive() {
        Resident r = savedResident("cancelled-rb@test.com");
        booking(r, LocalDate.of(2026, 6, 15), "Maple Hall", ResidentBookingStatus.CANCELLED);

        boolean exists = bookingRepo
                .existsByResidentIdAndRequestedDateAndBuildingNameIgnoreCaseAndStatusIn(
                        r.getId(),
                        LocalDate.of(2026, 6, 15),
                        "Maple Hall",
                        EnumSet.of(ResidentBookingStatus.REQUESTED,
                                   ResidentBookingStatus.CONFIRMED));

        assertThat(exists).isFalse();
    }

    @Test
    void existsByDuplicate_returnsFalse_whenDateDiffers() {
        Resident r = savedResident("diffdate-rb@test.com");
        booking(r, LocalDate.of(2026, 6, 15), "Maple Hall", ResidentBookingStatus.REQUESTED);

        boolean exists = bookingRepo
                .existsByResidentIdAndRequestedDateAndBuildingNameIgnoreCaseAndStatusIn(
                        r.getId(),
                        LocalDate.of(2026, 6, 16),
                        "Maple Hall",
                        EnumSet.of(ResidentBookingStatus.REQUESTED));

        assertThat(exists).isFalse();
    }

    @Test
    void existsByDuplicate_returnsFalse_whenBuildingDiffers() {
        Resident r = savedResident("diffbldg-rb@test.com");
        booking(r, LocalDate.of(2026, 7, 1), "Hall A", ResidentBookingStatus.CONFIRMED);

        boolean exists = bookingRepo
                .existsByResidentIdAndRequestedDateAndBuildingNameIgnoreCaseAndStatusIn(
                        r.getId(),
                        LocalDate.of(2026, 7, 1),
                        "Hall B",
                        EnumSet.of(ResidentBookingStatus.CONFIRMED));

        assertThat(exists).isFalse();
    }

    @TestConfiguration
    @EnableJpaAuditing
    static class AuditingConfig {}
}
