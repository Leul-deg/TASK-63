package com.reslife.api.domain.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.UUID;

public interface FailedLoginAttemptRepository extends JpaRepository<FailedLoginAttempt, UUID> {

    /**
     * Count recent failed attempts for a specific user within a rolling time window.
     * Used to enforce the 5-attempt / 15-minute lockout rule.
     */
    @Query("SELECT COUNT(a) FROM FailedLoginAttempt a " +
           "WHERE a.user = :user AND a.succeeded = false AND a.attemptedAt >= :since")
    long countRecentFailures(User user, Instant since);

    /**
     * Housekeeping: delete attempt records older than the given cutoff.
     * Called nightly by the purge scheduler.
     */
    @Modifying
    @Query("DELETE FROM FailedLoginAttempt a WHERE a.attemptedAt < :before")
    int deleteOlderThan(Instant before);
}
