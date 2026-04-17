package com.reslife.api.domain.housing;

import com.reslife.api.domain.resident.Resident;
import com.reslife.api.domain.resident.ResidentService;
import com.reslife.api.domain.user.User;
import com.reslife.api.domain.user.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ResidentBookingServiceTest {

    private final ResidentBookingRepository bookingRepository =
            mock(ResidentBookingRepository.class);
    private final ResidentService residentService = mock(ResidentService.class);
    private final BookingPolicyEnforcementService policyService =
            mock(BookingPolicyEnforcementService.class);
    private final UserRepository userRepository = mock(UserRepository.class);

    private ResidentBookingService service;

    private static final UUID RESIDENT_ID = UUID.randomUUID();
    private static final UUID BOOKING_ID  = UUID.randomUUID();
    private static final UUID ACTOR_ID    = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ResidentBookingService(
                bookingRepository, residentService, policyService, userRepository);
    }

    // ── findByResident ────────────────────────────────────────────────────────

    @Test
    void findByResident_delegatesToRepository() {
        Resident resident = mock(Resident.class);
        when(residentService.findById(RESIDENT_ID)).thenReturn(resident);
        when(bookingRepository.findByResidentIdOrderByRequestedDateDescCreatedAtDesc(RESIDENT_ID))
                .thenReturn(List.of());

        List<ResidentBooking> result = service.findByResident(RESIDENT_ID);

        assertThat(result).isEmpty();
        verify(bookingRepository).findByResidentIdOrderByRequestedDateDescCreatedAtDesc(RESIDENT_ID);
    }

    @Test
    void findByResident_throwsWhenResidentNotFound() {
        when(residentService.findById(RESIDENT_ID))
                .thenThrow(new EntityNotFoundException("Resident not found"));

        assertThatThrownBy(() -> service.findByResident(RESIDENT_ID))
                .isInstanceOf(EntityNotFoundException.class);
    }

    // ── createBooking ─────────────────────────────────────────────────────────

    @Test
    void createBooking_success() {
        Resident resident = mock(Resident.class);
        User actor = new User();

        when(residentService.findById(RESIDENT_ID)).thenReturn(resident);
        when(policyService.evaluate(eq(RESIDENT_ID), any()))
                .thenReturn(new BookingPolicyCheckResponse(true, false, null, null, null, null, 0L, false, List.of()));
        when(bookingRepository.existsByResidentIdAndRequestedDateAndBuildingNameIgnoreCaseAndStatusIn(
                any(), any(), any(), any())).thenReturn(false);
        when(userRepository.findById(ACTOR_ID)).thenReturn(Optional.of(actor));
        when(bookingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ResidentBookingRequest request = new ResidentBookingRequest(
                LocalDate.of(2026, 5, 1), "Maple Hall", "101", "Visit", null);
        ResidentBooking result = service.createBooking(RESIDENT_ID, request, ACTOR_ID);

        assertThat(result.getBuildingName()).isEqualTo("Maple Hall");
        assertThat(result.getStatus()).isEqualTo(ResidentBookingStatus.REQUESTED);
        assertThat(result.getCreatedBy()).isSameAs(actor);
        verify(bookingRepository).save(any());
    }

    @Test
    void createBooking_trimsWhitespaceFromBuildingName() {
        Resident resident = mock(Resident.class);
        when(residentService.findById(RESIDENT_ID)).thenReturn(resident);
        when(policyService.evaluate(eq(RESIDENT_ID), any()))
                .thenReturn(new BookingPolicyCheckResponse(true, false, null, null, null, null, 0L, false, List.of()));
        when(bookingRepository.existsByResidentIdAndRequestedDateAndBuildingNameIgnoreCaseAndStatusIn(
                any(), any(), any(), any())).thenReturn(false);
        when(bookingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ResidentBookingRequest request = new ResidentBookingRequest(
                LocalDate.of(2026, 5, 1), "  Maple Hall  ", null, null, null);
        ResidentBooking result = service.createBooking(RESIDENT_ID, request, null);

        assertThat(result.getBuildingName()).isEqualTo("Maple Hall");
    }

    @Test
    void createBooking_throwsWhenPolicyDenies() {
        Resident resident = mock(Resident.class);
        when(residentService.findById(RESIDENT_ID)).thenReturn(resident);
        when(policyService.evaluate(eq(RESIDENT_ID), any()))
                .thenReturn(new BookingPolicyCheckResponse(false, false, null, null, null, null, 0L, false, List.of("Outside booking window")));

        ResidentBookingRequest request = new ResidentBookingRequest(
                LocalDate.of(2020, 1, 1), "Maple Hall", null, null, null);

        assertThatThrownBy(() -> service.createBooking(RESIDENT_ID, request, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Outside booking window");

        verify(bookingRepository, never()).save(any());
    }

    @Test
    void createBooking_throwsWhenDuplicateActiveBookingExists() {
        Resident resident = mock(Resident.class);
        when(residentService.findById(RESIDENT_ID)).thenReturn(resident);
        when(policyService.evaluate(eq(RESIDENT_ID), any()))
                .thenReturn(new BookingPolicyCheckResponse(true, false, null, null, null, null, 0L, false, List.of()));
        when(bookingRepository.existsByResidentIdAndRequestedDateAndBuildingNameIgnoreCaseAndStatusIn(
                any(), any(), any(), any())).thenReturn(true);

        ResidentBookingRequest request = new ResidentBookingRequest(
                LocalDate.of(2026, 5, 1), "Maple Hall", null, null, null);

        assertThatThrownBy(() -> service.createBooking(RESIDENT_ID, request, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("active booking already exists");
    }

    // ── updateStatus ──────────────────────────────────────────────────────────

    @Test
    void updateStatus_success() {
        Resident resident = mock(Resident.class);
        when(resident.getId()).thenReturn(RESIDENT_ID);

        ResidentBooking booking = new ResidentBooking();
        booking.setResident(resident);
        booking.setStatus(ResidentBookingStatus.REQUESTED);

        when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.of(booking));
        when(bookingRepository.save(booking)).thenReturn(booking);

        ResidentBooking result = service.updateStatus(
                RESIDENT_ID, BOOKING_ID, ResidentBookingStatus.CONFIRMED, "Approved", null);

        assertThat(result.getStatus()).isEqualTo(ResidentBookingStatus.CONFIRMED);
        assertThat(result.getDecisionReason()).isEqualTo("Approved");
        verify(bookingRepository).save(booking);
    }

    @Test
    void updateStatus_blankReasonBecomesNull() {
        Resident resident = mock(Resident.class);
        when(resident.getId()).thenReturn(RESIDENT_ID);

        ResidentBooking booking = new ResidentBooking();
        booking.setResident(resident);

        when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.of(booking));
        when(bookingRepository.save(booking)).thenReturn(booking);

        service.updateStatus(RESIDENT_ID, BOOKING_ID, ResidentBookingStatus.CANCELLED, "   ", null);

        assertThat(booking.getDecisionReason()).isNull();
    }

    @Test
    void updateStatus_throwsWhenBookingNotFound() {
        when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateStatus(
                RESIDENT_ID, BOOKING_ID, ResidentBookingStatus.CANCELLED, null, null))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining(BOOKING_ID.toString());
    }

    @Test
    void updateStatus_throwsWhenBookingBelongsToDifferentResident() {
        UUID otherResidentId = UUID.randomUUID();
        Resident other = mock(Resident.class);
        when(other.getId()).thenReturn(otherResidentId);

        ResidentBooking booking = new ResidentBooking();
        booking.setResident(other);

        when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> service.updateStatus(
                RESIDENT_ID, BOOKING_ID, ResidentBookingStatus.CANCELLED, null, null))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining(RESIDENT_ID.toString());
    }
}
