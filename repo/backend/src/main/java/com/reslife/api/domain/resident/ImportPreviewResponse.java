package com.reslife.api.domain.resident;

import java.util.List;

/**
 * Full parse-and-validate result returned by
 * {@code POST /api/residents/import/preview}.
 *
 * <p>No data has been written to the database at this point. The operator
 * reviews the rows, decides how to handle each
 * {@link ImportRowPreview.RowStatus#MERGE_CANDIDATE}, and then submits a
 * commit request.
 */
public record ImportPreviewResponse(
        int                    totalRows,
        int                    newCount,
        int                    mergeCount,
        int                    invalidCount,
        List<ImportRowPreview> rows
) {}
