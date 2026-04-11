package com.reslife.api.domain.crawler;

import java.time.Instant;
import java.util.UUID;

/** Read-only projection of a {@link CrawlJob} for API responses. */
public record JobResponse(
        UUID    id,
        UUID    sourceId,
        String  sourceName,
        String  triggerType,
        String  status,
        int     pagesCrawled,
        int     pagesSkipped,
        int     pagesFailed,
        int     itemsFound,
        Instant startedAt,
        Instant pausedAt,
        Instant finishedAt,
        String  errorMessage,
        Instant createdAt
) {
    public static JobResponse from(CrawlJob j) {
        return new JobResponse(
                j.getId(),
                j.getSource().getId(),
                j.getSource().getName(),
                j.getTriggerType().name(),
                j.getStatus().name(),
                j.getPagesCrawled(),
                j.getPagesSkipped(),
                j.getPagesFailed(),
                j.getItemsFound(),
                j.getStartedAt(),
                j.getPausedAt(),
                j.getFinishedAt(),
                j.getErrorMessage(),
                j.getCreatedAt()
        );
    }
}
