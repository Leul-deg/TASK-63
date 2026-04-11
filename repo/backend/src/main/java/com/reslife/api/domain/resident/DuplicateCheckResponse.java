package com.reslife.api.domain.resident;

import java.util.List;
import java.util.UUID;

/**
 * Response for the duplicate-candidate check before creating a resident.
 *
 * <p>A 409 response from {@code POST /api/residents} will carry this body
 * so the frontend can show the conflicting records and let the user decide
 * whether to save anyway.
 */
public record DuplicateCheckResponse(List<Candidate> candidates) {

    public boolean hasDuplicates() {
        return !candidates.isEmpty();
    }

    /**
     * One potential duplicate.
     *
     * @param matchReason  which field triggered the match: {@code "email"},
     *                     {@code "studentId"}, or {@code "name+dob"}
     */
    public record Candidate(
            UUID   id,
            String studentId,
            String firstName,
            String lastName,
            String email,
            String matchReason
    ) {}
}
