package com.reslife.api.domain.crawler;

public enum TriggerType {
    /** Fired automatically by the cron/interval schedule. */
    SCHEDULED,
    /** Triggered manually by an admin via the API. */
    MANUAL
}
