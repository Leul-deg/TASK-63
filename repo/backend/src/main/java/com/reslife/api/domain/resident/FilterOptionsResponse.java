package com.reslife.api.domain.resident;

import java.util.List;

/**
 * Payload for the GET /api/residents/filter-options endpoint.
 * Drives the building and class-year filter dropdowns in the UI.
 */
public record FilterOptionsResponse(
        List<String>  buildings,
        List<Integer> classYears
) {}
