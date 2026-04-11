package com.reslife.api.domain.housing;

import java.time.LocalDate;
import java.util.List;

/**
 * Result of evaluating a requested booking against the active policy.
 */
public record BookingPolicyCheckResponse(
        boolean allowed,
        boolean policyApplied,
        String buildingName,
        LocalDate requestedDate,
        LocalDate latestAllowedDate,
        String sameDayCutoffTime,
        long recentNoShowCount,
        boolean noShowRestrictionActive,
        List<String> reasons
) {}
