package com.reslife.api.domain.crawler;

import java.time.Instant;
import java.util.UUID;

/** Read-only projection of a {@link CrawlSource} for API responses. */
public record SourceResponse(
        UUID    id,
        String  name,
        String  baseUrl,
        String  siteType,
        String  description,
        String  city,
        String  keywords,
        String  crawlConfig,
        String  scheduleCron,
        Long    scheduleIntervalSeconds,
        int     delayMsBetweenRequests,
        int     maxDepth,
        int     maxPages,
        boolean active,
        Instant lastCrawledAt,
        Instant createdAt,
        Instant updatedAt
) {
    public static SourceResponse from(CrawlSource s) {
        return new SourceResponse(
                s.getId(),
                s.getName(),
                s.getBaseUrl(),
                s.getSiteType().name(),
                s.getDescription(),
                s.getCity(),
                s.getKeywords(),
                s.getCrawlConfig(),
                s.getScheduleCron(),
                s.getScheduleIntervalSeconds(),
                s.getDelayMsBetweenRequests(),
                s.getMaxDepth(),
                s.getMaxPages(),
                s.isActive(),
                s.getLastCrawledAt(),
                s.getCreatedAt(),
                s.getUpdatedAt()
        );
    }
}
