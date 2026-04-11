package com.reslife.api.domain.resident;

import java.util.List;

/**
 * Result of {@code POST /api/residents/import/commit}.
 *
 * <p>Each row is independently attempted so a single bad row does not abort
 * the rest of the import.  Failures are returned in {@link #failures} for
 * the operator to review.
 */
public record ImportCommitResponse(
        int                  created,
        int                  merged,
        int                  skipped,
        int                  failed,
        List<RowFailure>     failures
) {

    /** Details for a row that could not be committed. */
    public record RowFailure(int rowNumber, String reason) {}

    public boolean hasFailures() {
        return !failures.isEmpty();
    }
}
