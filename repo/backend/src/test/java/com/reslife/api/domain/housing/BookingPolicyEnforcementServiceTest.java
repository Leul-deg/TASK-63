package com.reslife.api.domain.housing;

import com.reslife.api.admin.BookingPolicy;
import com.reslife.api.admin.BookingPolicyService;
import com.reslife.api.domain.resident.Resident;
import com.reslife.api.domain.resident.ResidentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BookingPolicyEnforcementServiceTest {

    private final BookingPolicyService bookingPolicyService = mock(BookingPolicyService.class);
    private final ResidentService residentService = mock(ResidentService.class);
    private final MoveInRecordRepository moveInRecordRepository = mock(MoveInRecordRepository.class);

    private BookingPolicyEnforcementService service;
    private Resident resident;
    private UUID residentId;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-04-10T15:30:00Z"), ZoneOffset.UTC);
        service = new BookingPolicyEnforcementService(
                bookingPolicyService, residentService, moveInRecordRepository, clock);

        residentId = UUID.randomUUID();
        resident = new Resident();
        resident.setBuildingName("Maple Hall");

        when(residentService.findById(residentId)).thenReturn(resident);
        when(moveInRecordRepository.findTopByResidentIdOrderByMoveInDateDesc(residentId))
                .thenReturn(Optional.empty());
        when(moveInRecordRepository.countByResidentIdAndCheckInStatusAndMoveInDateGreaterThanEqual(
                eq(residentId), eq(CheckInStatus.NO_SHOW), any(LocalDate.class)))
                .thenReturn(0L);
    }

    @Test
    void rejectsDatesOutsideRollingWindow() {
        when(bookingPolicyService.getPolicy()).thenReturn(defaultPolicy());

        BookingPolicyCheckResponse response = service.evaluate(
                residentId,
                new BookingPolicyCheckRequest(LocalDate.of(2026, 4, 30), "Maple Hall"));

        assertFalse(response.allowed());
        assertTrue(response.policyApplied());
        assertTrue(response.reasons().stream().anyMatch(r -> r.contains("outside the 14-day booking window")));
    }

    @Test
    void rejectsSameDayBookingsAfterCutoff() {
        when(bookingPolicyService.getPolicy()).thenReturn(defaultPolicy());
        Clock afterCutoffClock = Clock.fixed(Instant.parse("2026-04-10T18:30:00Z"), ZoneOffset.UTC);
        BookingPolicyEnforcementService afterCutoffService = new BookingPolicyEnforcementService(
                bookingPolicyService, residentService, moveInRecordRepository, afterCutoffClock);

        BookingPolicyCheckResponse response = afterCutoffService.evaluate(
                residentId,
                new BookingPolicyCheckRequest(LocalDate.of(2026, 4, 10), "Maple Hall"));

        assertFalse(response.allowed());
        assertTrue(response.reasons().stream().anyMatch(r -> r.contains("Same-day bookings close at 17:00")));
    }

    @Test
    void rejectsResidentsWhoHitNoShowThreshold() {
        when(bookingPolicyService.getPolicy()).thenReturn(defaultPolicy());
        when(moveInRecordRepository.countByResidentIdAndCheckInStatusAndMoveInDateGreaterThanEqual(
                eq(residentId), eq(CheckInStatus.NO_SHOW), any(LocalDate.class)))
                .thenReturn(2L);

        BookingPolicyCheckResponse response = service.evaluate(
                residentId,
                new BookingPolicyCheckRequest(LocalDate.of(2026, 4, 12), "Maple Hall"));

        assertFalse(response.allowed());
        assertTrue(response.noShowRestrictionActive());
        assertEquals(2L, response.recentNoShowCount());
    }

    @Test
    void allowsRequestsOutsideCanaryCohort() {
        BookingPolicy policy = defaultPolicy();
        policy.setCanaryEnabled(true);

        when(bookingPolicyService.getPolicy()).thenReturn(policy);
        when(bookingPolicyService.isInCanaryCohort("Cedar Hall")).thenReturn(false);

        BookingPolicyCheckResponse response = service.evaluate(
                residentId,
                new BookingPolicyCheckRequest(LocalDate.of(2026, 4, 12), "Cedar Hall"));

        assertTrue(response.allowed());
        assertFalse(response.policyApplied());
        assertTrue(response.reasons().stream().anyMatch(r -> r.contains("does not yet apply")));
    }

    private BookingPolicy defaultPolicy() {
        BookingPolicy policy = new BookingPolicy();
        policy.setWindowDays(14);
        policy.setSameDayCutoffHour(17);
        policy.setSameDayCutoffMinute(0);
        policy.setNoShowThreshold(2);
        policy.setNoShowWindowDays(30);
        policy.setCanaryEnabled(false);
        policy.setCanaryRolloutPercent(10);
        return policy;
    }
}
