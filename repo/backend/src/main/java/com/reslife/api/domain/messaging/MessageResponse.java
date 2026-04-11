package com.reslife.api.domain.messaging;

import java.time.Instant;
import java.util.UUID;

/**
 * Wire representation of a single message.
 *
 * <p>{@code status} is populated from the <em>sender's</em> perspective:
 * {@code SENT} until a recipient's device fetches the message via poll,
 * {@code DELIVERED} once any recipient device has polled but none has opened the thread,
 * {@code READ} once at least one recipient has opened the thread.
 * For messages sent by someone else it is {@code null}.
 */
public record MessageResponse(
        UUID    id,
        UUID    senderId,
        String  senderDisplayName,
        String  body,
        String  messageType,
        String  imageUrl,
        String  quickReplyKey,
        boolean deleted,
        Instant createdAt,
        String  status          // "SENT" | "DELIVERED" | "READ" | null
) {
    public static MessageResponse from(Message m, String imageBaseUrl,
                                       String status, String senderDisplayName) {
        String imageUrl = (m.getImageFilename() != null)
                ? imageBaseUrl + "/" + m.getImageFilename()
                : null;
        return new MessageResponse(
                m.getId(),
                m.getSender() != null ? m.getSender().getId() : null,
                senderDisplayName,
                m.getBody(),
                m.getMessageType().name(),
                imageUrl,
                m.getQuickReplyKey(),
                m.isDeleted(),
                m.getCreatedAt(),
                status
        );
    }
}
