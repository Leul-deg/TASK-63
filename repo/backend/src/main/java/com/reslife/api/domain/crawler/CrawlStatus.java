package com.reslife.api.domain.crawler;

public enum CrawlStatus {
    /** Created, waiting for an engine slot. */
    PENDING,
    /** Actively crawling. */
    RUNNING,
    /** Paused by operator — checkpoint saved, resumable. */
    PAUSED,
    /** Finished normally. */
    COMPLETED,
    /** Stopped due to an unrecoverable error. */
    FAILED,
    /** Explicitly cancelled by an operator. */
    CANCELLED;

    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == CANCELLED;
    }

    public boolean isActive() {
        return this == PENDING || this == RUNNING;
    }
}
