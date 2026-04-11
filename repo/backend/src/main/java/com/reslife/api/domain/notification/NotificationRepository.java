package com.reslife.api.domain.notification;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    /**
     * Inbox query: optional read-state filter, optional category filter.
     * Unread items and critical/required-ack items surface first.
     */
    @Query("SELECT n FROM Notification n WHERE n.recipient.id = :userId " +
           "AND (:readFilter IS NULL OR n.read = :readFilter) " +
           "AND (:category  IS NULL OR n.category = :category) " +
           "ORDER BY n.read ASC, n.requiresAcknowledgment DESC, " +
           "  CASE n.priority WHEN 'CRITICAL' THEN 0 WHEN 'HIGH' THEN 1 WHEN 'NORMAL' THEN 2 ELSE 3 END ASC, " +
           "  n.createdAt DESC")
    Page<Notification> findInbox(@Param("userId") UUID userId,
                                 @Param("readFilter") Boolean readFilter,
                                 @Param("category") NotificationCategory category,
                                 Pageable pageable);

    long countByRecipientIdAndReadFalse(UUID recipientId);

    long countByRecipientIdAndRequiresAcknowledgmentTrueAndAcknowledgedAtIsNull(UUID recipientId);

    @Modifying
    @Query("UPDATE Notification n SET n.read = true, n.readAt = CURRENT_TIMESTAMP " +
           "WHERE n.recipient.id = :recipientId AND n.read = false")
    int markAllReadForRecipient(@Param("recipientId") UUID recipientId);
}
