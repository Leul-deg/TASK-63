package com.reslife.api.domain.crawler;

public enum PageStatus {
    /** Queued, not yet fetched. */
    PENDING,
    /** HTTP request in flight. */
    FETCHING,
    /** Successfully fetched and content has changed since last crawl. */
    FETCHED,
    /** Fetched but content hash matched the previous crawl — no reprocessing needed. */
    SKIPPED,
    /** Fetch failed (network error, non-2xx response, etc.). */
    ERROR
}
