package com.reslife.api.domain.resident;

import java.util.List;
import java.util.UUID;

/**
 * Preview result for a single CSV row.
 *
 * <p>The frontend uses {@link RowStatus} to determine row colouring and
 * which controls to show (merge/skip toggles appear only for
 * {@link RowStatus#MERGE_CANDIDATE} rows).
 */
public record ImportRowPreview(
        /** 1-based row number (header row is not counted). */
        int             rowNumber,
        RowStatus       status,
        ImportRowData   data,
        /** Field-level validation error messages; empty when the row is valid. */
        List<String>    errors,
        /**
         * The existing resident that matches this row, or {@code null} when
         * no duplicate was found.
         */
        ExistingMatch   match
) {

    public enum RowStatus {
        /** No matching resident — will be created on commit. */
        NEW,
        /**
         * A matching resident was found. Operator chooses merge or skip.
         * The row itself must be valid (no {@link #errors}) to be mergeable.
         */
        MERGE_CANDIDATE,
        /**
         * One or more validation errors. The row cannot be committed; the
         * operator must fix the source file and re-upload.
         */
        INVALID
    }

    /**
     * Compact view of the resident that triggered the duplicate match.
     *
     * @param matchReason  {@code "studentId"} or {@code "name+dob"}
     */
    public record ExistingMatch(
            UUID   id,
            String studentId,
            String firstName,
            String lastName,
            String email,
            String matchReason,
            Integer sourceRowNumber
    ) {}
}
