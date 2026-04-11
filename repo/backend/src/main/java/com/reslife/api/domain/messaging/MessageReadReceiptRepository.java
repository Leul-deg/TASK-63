package com.reslife.api.domain.messaging;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface MessageReadReceiptRepository extends JpaRepository<MessageReadReceipt, MessageReadReceiptId> {

    /**
     * IDs of messages in a given set that this user+session has read.
     */
    @Query("SELECT r.id.messageId FROM MessageReadReceipt r " +
           "WHERE r.id.messageId IN :messageIds AND r.id.readerUserId = :userId AND r.id.sessionId = :sessionId")
    Set<UUID> findReadMessageIds(@Param("messageIds") List<UUID> messageIds,
                                 @Param("userId") UUID userId,
                                 @Param("sessionId") String sessionId);

    /**
     * For each message ID in a thread, how many distinct non-sender users have read it.
     * Used to compute sent/read status shown to the sender.
     * Returns Object[]{messageId, count}.
     */
    @Query("SELECT r.id.messageId, COUNT(DISTINCT r.id.readerUserId) FROM MessageReadReceipt r " +
           "WHERE r.id.messageId IN :messageIds AND r.id.readerUserId != :senderId " +
           "GROUP BY r.id.messageId")
    List<Object[]> countReadsByMessageExcludingSender(@Param("messageIds") List<UUID> messageIds,
                                                      @Param("senderId") UUID senderId);

    /**
     * Count messages in a thread not yet read by this user+session.
     */
    @Query("SELECT COUNT(m) FROM Message m " +
           "WHERE m.thread.id = :threadId " +
           "  AND m.sender.id != :userId " +
           "  AND NOT EXISTS (" +
           "      SELECT 1 FROM MessageReadReceipt r " +
           "      WHERE r.id.messageId = m.id " +
           "        AND r.id.readerUserId = :userId " +
           "        AND r.id.sessionId = :sessionId)")
    long countUnread(@Param("threadId") UUID threadId,
                     @Param("userId") UUID userId,
                     @Param("sessionId") String sessionId);
}
