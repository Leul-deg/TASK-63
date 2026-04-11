package com.reslife.api.domain.messaging;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;

import java.io.Serializable;
import java.util.UUID;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class MessageReadReceiptId implements Serializable {

    @Column(name = "message_id", nullable = false)
    private UUID messageId;

    @Column(name = "reader_user_id", nullable = false)
    private UUID readerUserId;

    @Column(name = "session_id", nullable = false, length = 255)
    private String sessionId;
}
