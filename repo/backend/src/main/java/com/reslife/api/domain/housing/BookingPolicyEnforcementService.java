package com.reslife.api.domain.housing;

import com.reslife.api.admin.BookingPolicy;
import com.reslife.api.admin.BookingPolicyService;
import com.reslife.api.domain.resident.Resident;
import com.reslife.api.domain.resident.ResidentService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Evaluates whether a requested booking date should be allowed under the
 * currently active booking policy.
 */
@Service
@Transactional(readOnly = true)
public class BookingPolicyEnforcementService {

    private final BookingPolicyService bookingPolicyService;
    private final ResidentService residentService;
    private final MoveInRecordRepository moveInRecordRepository;
    private final Clock clock;

    public BookingPolicyEnforcementService(BookingPolicyService bookingPolicyService,
                                           ResidentService residentService,
                                           MoveInRecordRepository moveInRecordRepository,
                                           Clock clock) {
        this.bookingPolicyService = bookingPolicyService;
        this.residentService = residentService;
        this.moveInRecordRepository = moveInRecordRepository;
        this.clock = clock;
    }

    public BookingPolicyCheckResponse evaluate(UUID residentId, BookingPolicyCheckRequest request) {
        Resident resident = residentService.findById(residentId);
        BookingPolicy policy = bookingPolicyService.getPolicy();
        LocalDate today = LocalDate.now(clock);
        LocalTime now = LocalTime.now(clock);
        LocalDate requestedDate = request.requestedDate();
        LocalDate latestAllowedDate = today.plusDays(policy.getWindowDays());
        LocalDate noShowWindowStart = today.minusDays(Math.max(0, policy.getNoShowWindowDays() - 1L));

        String buildingName = resolveBuildingName(request.buildingName(), residentId, resident);
        long recentNoShowCount = moveInRecordRepository
                .countByResidentIdAndCheckInStatusAndMoveInDateGreaterThanEqual(
                        residentId, CheckInStatus.NO_SHOW, noShowWindowStart);
        boolean noShowRestrictionActive = recentNoShowCount >= policy.getNoShowThreshold();

        List<String> reasons = new ArrayList<>();
        boolean policyApplied = !policy.isCanaryEnabled();

        if (requestedDate.isBefore(today)) {
            reasons.add("Requested date cannot be in the past.");
        }

        if (policy.isCanaryEnabled()) {
            if (buildingName == null) {
                reasons.add("Building is required while canary rollout is enabled.");
            } else {
                policyApplied = bookingPolicyService.isInCanaryCohort(buildingName);
            }
        }

        if (policyApplied) {
            if (requestedDate.isAfter(latestAllowedDate)) {
                reasons.add("Requested date is outside the " + policy.getWindowDays() + "-day booking window.");
            }
            if (isHolidayBlackout(policy, requestedDate)) {
                reasons.add("Requested date falls on a configured holiday blackout.");
            }
            if (requestedDate.equals(today)) {
                LocalTime cutoff = LocalTime.of(policy.getSameDayCutoffHour(), policy.getSameDayCutoffMinute());
                if (!now.isBefore(cutoff)) {
                    reasons.add("Same-day bookings close at " + formatCutoff(policy) + ".");
                }
            }
            if (noShowRestrictionActive) {
                reasons.add("Resident has reached the no-show limit for the active policy window.");
            }
        }

        if (!policyApplied && reasons.isEmpty()) {
            reasons.add("Policy is in canary rollout and does not yet apply to this building.");
        }

        return new BookingPolicyCheckResponse(
                reasons.stream().noneMatch(reason -> !reason.contains("does not yet apply")),
                policyApplied,
                buildingName,
                requestedDate,
                latestAllowedDate,
                formatCutoff(policy),
                recentNoShowCount,
                noShowRestrictionActive,
                List.copyOf(reasons)
        );
    }

    private String resolveBuildingName(String requestedBuildingName, UUID residentId, Resident resident) {
        if (requestedBuildingName != null && !requestedBuildingName.isBlank()) {
            return requestedBuildingName.trim();
        }
        if (resident.getBuildingName() != null && !resident.getBuildingName().isBlank()) {
            return resident.getBuildingName().trim();
        }
        return moveInRecordRepository.findTopByResidentIdOrderByMoveInDateDesc(residentId)
                .map(MoveInRecord::getBuildingName)
                .filter(name -> name != null && !name.isBlank())
                .map(String::trim)
                .orElse(null);
    }

    private boolean isHolidayBlackout(BookingPolicy policy, LocalDate requestedDate) {
        return policy.getHolidayBlackoutDates().stream()
                .anyMatch(blackout -> requestedDate.toString().equals(blackout.getDate()));
    }

    private String formatCutoff(BookingPolicy policy) {
        return String.format(Locale.ROOT, "%02d:%02d",
                policy.getSameDayCutoffHour(),
                policy.getSameDayCutoffMinute());
    }
}
