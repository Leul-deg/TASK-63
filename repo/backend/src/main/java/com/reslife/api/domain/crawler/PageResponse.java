package com.reslife.api.domain.crawler;

import java.time.Instant;
import java.util.UUID;

/** Read-only projection of a {@link CrawlPage} for API responses. */
public record PageResponse(
        UUID    id,
        String  url,
        String  status,
        Integer httpStatus,
        String  contentHash,
        Integer contentLength,
        int     depth,
        String  errorMessage,
        Instant fetchedAt,
        Instant createdAt
) {
    public static PageResponse from(CrawlPage p) {
        return new PageResponse(
                p.getId(),
                p.getUrl(),
                p.getStatus().name(),
                p.getHttpStatus(),
                p.getContentHash(),
                p.getContentLength(),
                p.getDepth(),
                p.getErrorMessage(),
                p.getFetchedAt(),
                p.getCreatedAt()
        );
    }
}
