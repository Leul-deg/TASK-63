package com.reslife.api.domain.resident;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.List;
import java.util.UUID;

/**
 * Operator decisions submitted to {@code POST /api/residents/import/commit}.
 *
 * <p>The frontend sends back each non-invalid row from the preview with an
 * action. {@link RowAction#SKIP} rows are ignored; the backend re-validates
 * all {@link RowAction#CREATE} and {@link RowAction#MERGE} rows before
 * writing.
 */
public record ImportCommitRequest(@NotEmpty @Valid List<CommitDecision> rows) {

    public record CommitDecision(

            int rowNumber,

            @NotNull
            RowAction action,

            /** Required when action is {@link RowAction#MERGE}. */
            UUID mergeTargetId,

            /**
             * Required when action is {@link RowAction#MERGE} and the operator chose
             * to merge this row into an earlier row from the same uploaded file
             * rather than into an existing database resident.
             */
            Integer mergeTargetRowNumber,

            @NotNull @Valid
            ImportRowData data

    ) {}

    public enum RowAction { CREATE, MERGE, SKIP }
}
