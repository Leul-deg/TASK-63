package com.reslife.api.domain.messaging;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Records that a message has been delivered to a specific recipient's device
 * via a background poll.  A single row per (message, recipient) — once any
 * device of a recipient has polled, the message is considered delivered.
 *
 * <p>Distinct from {@link MessageReadReceipt}: delivery happens when the client
 * fetches new messages in the background; read happens when the user opens the thread.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "message_delivery_receipts")
public class MessageDeliveryReceipt {

    @EmbeddedId
    private MessageDeliveryReceiptId id;

    @Column(name = "delivered_at", nullable = false)
    private Instant deliveredAt = Instant.now();

    public MessageDeliveryReceipt(UUID messageId, UUID recipientUserId) {
        this.id = new MessageDeliveryReceiptId(messageId, recipientUserId);
        this.deliveredAt = Instant.now();
    }
}
