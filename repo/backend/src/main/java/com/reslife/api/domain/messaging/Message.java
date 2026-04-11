package com.reslife.api.domain.messaging;

import com.reslife.api.common.SoftDeletableEntity;
import com.reslife.api.domain.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "messages")
@SQLRestriction("deleted_at IS NULL")
public class Message extends SoftDeletableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "thread_id", nullable = false)
    private MessageThread thread;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id")
    private User sender;

    @Column(columnDefinition = "TEXT")
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(name = "message_type", nullable = false, length = 20)
    private MessageType messageType = MessageType.TEXT;

    @Column(name = "image_filename", length = 255)
    private String imageFilename;

    @Column(name = "quick_reply_key", length = 100)
    private String quickReplyKey;

    /** Legacy per-thread flag — superseded by message_read_receipts for per-device tracking. */
    @Column(name = "is_read", nullable = false)
    private boolean read = false;
}
