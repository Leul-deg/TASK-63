package com.reslife.api.domain.crawler;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request body for creating or updating a {@link CrawlSource}.
 */
public record CreateSourceRequest(

        @NotBlank @Size(max = 255)
        String name,

        @NotBlank @Size(max = 1000)
        String baseUrl,

        @NotNull
        SiteType siteType,

        String description,

        @Size(max = 100)
        String city,

        /** JSON array of keyword strings, e.g. {@code ["vacancy","available"]}. */
        String keywords,

        /**
         * JSON-serialized {@link CrawlConfig}.
         * Pass {@code null} or {@code "{}"} for defaults.
         */
        String crawlConfig,

        /** Spring cron expression — mutually exclusive with {@code scheduleIntervalSeconds}. */
        String scheduleCron,

        /** Seconds between runs — mutually exclusive with {@code scheduleCron}. */
        Long scheduleIntervalSeconds,

        @Min(1)
        int delayMsBetweenRequests,

        @Min(1)
        int maxDepth,

        @Min(1)
        int maxPages,

        boolean active
) {
    /** Applies sensible defaults when omitted values are null/zero. */
    public CreateSourceRequest {
        if (siteType == null)             siteType = SiteType.HTML;
        if (delayMsBetweenRequests <= 0)  delayMsBetweenRequests = 1_000;
        if (maxDepth <= 0)                maxDepth = 3;
        if (maxPages <= 0)                maxPages = 100;
    }
}
