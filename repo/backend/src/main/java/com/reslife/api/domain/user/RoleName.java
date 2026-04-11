package com.reslife.api.domain.user;

public enum RoleName {
    ADMIN,
    DIRECTOR,
    RESIDENT_DIRECTOR,
    RESIDENT_ASSISTANT,
    STAFF,
    READ_ONLY,
    /** Manages housing assignments and operations; full sensitive-data access. */
    HOUSING_ADMINISTRATOR,
    /** Day-to-day residence staff; full sensitive-data access. */
    RESIDENCE_STAFF,
    /** Student portal; own housing info only — sensitive fields of others are redacted. */
    STUDENT
}
