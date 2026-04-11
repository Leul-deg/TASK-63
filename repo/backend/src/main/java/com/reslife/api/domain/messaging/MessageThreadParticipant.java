package com.reslife.api.domain.messaging;

import com.reslife.api.domain.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "message_thread_participants")
public class MessageThreadParticipant {

    @EmbeddedId
    private MessageThreadParticipantId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("threadId")
    @JoinColumn(name = "thread_id", nullable = false)
    private MessageThread thread;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("userId")
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "joined_at", nullable = false, updatable = false)
    private Instant joinedAt;

    @PrePersist
    protected void onCreate() {
        joinedAt = Instant.now();
    }

    public MessageThreadParticipant(MessageThread thread, User user) {
        this.thread = thread;
        this.user = user;
        this.id = new MessageThreadParticipantId(thread.getId(), user.getId());
    }
}
