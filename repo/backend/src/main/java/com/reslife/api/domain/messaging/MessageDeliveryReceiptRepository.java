package com.reslife.api.domain.messaging;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface MessageDeliveryReceiptRepository
        extends JpaRepository<MessageDeliveryReceipt, MessageDeliveryReceiptId> {

    /**
     * IDs of messages (from the given set) already delivered to this recipient.
     * Used to skip inserting duplicate delivery receipts.
     */
    @Query("SELECT d.id.messageId FROM MessageDeliveryReceipt d " +
           "WHERE d.id.messageId IN :messageIds AND d.id.recipientUserId = :recipientUserId")
    Set<UUID> findDeliveredMessageIds(@Param("messageIds") List<UUID> messageIds,
                                      @Param("recipientUserId") UUID recipientUserId);

    /**
     * For each message ID, how many distinct non-sender recipients have a delivery receipt.
     * Used to compute DELIVERED status shown to the sender.
     * Returns {@code Object[]{messageId, count}}.
     */
    @Query("SELECT d.id.messageId, COUNT(DISTINCT d.id.recipientUserId) " +
           "FROM MessageDeliveryReceipt d " +
           "WHERE d.id.messageId IN :messageIds AND d.id.recipientUserId != :senderId " +
           "GROUP BY d.id.messageId")
    List<Object[]> countDeliveriesByMessageExcludingSender(@Param("messageIds") List<UUID> messageIds,
                                                            @Param("senderId") UUID senderId);
}
