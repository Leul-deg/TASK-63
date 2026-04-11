package com.reslife.api.domain.messaging;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Records that a specific user/session has read a message.
 *
 * <p>Inserted automatically when a participant fetches a thread. Delivery while
 * polling is tracked separately in {@link MessageDeliveryReceipt}. The
 * (message_id, reader_user_id, session_id) triple is the natural key —
 * per-device read state is tracked through the session_id.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "message_read_receipts")
public class MessageReadReceipt {

    @EmbeddedId
    private MessageReadReceiptId id;

    @Column(name = "read_at", nullable = false)
    private Instant readAt = Instant.now();

    public MessageReadReceipt(UUID messageId, UUID readerUserId, String sessionId) {
        this.id = new MessageReadReceiptId(messageId, readerUserId, sessionId);
        this.readAt = Instant.now();
    }
}
