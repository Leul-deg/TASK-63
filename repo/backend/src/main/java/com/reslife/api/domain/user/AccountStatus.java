package com.reslife.api.domain.user;

public enum AccountStatus {
    /** Normal — can log in. */
    ACTIVE,
    /** Disabled by an admin. Reversible. Scheduled for purge after 30 days. */
    DISABLED,
    /** Temporarily frozen by an admin. No purge clock started. */
    FROZEN,
    /** Blacklisted for policy violation. Not reversible via normal flow. */
    BLACKLISTED
}
