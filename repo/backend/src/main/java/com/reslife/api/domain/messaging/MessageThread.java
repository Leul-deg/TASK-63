package com.reslife.api.domain.messaging;

import com.reslife.api.common.SoftDeletableEntity;
import com.reslife.api.domain.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "message_threads")
@SQLRestriction("deleted_at IS NULL")
public class MessageThread extends SoftDeletableEntity {

    @Column(length = 255)
    private String subject;

    @Enumerated(EnumType.STRING)
    @Column(name = "thread_type", nullable = false, length = 50)
    private ThreadType threadType = ThreadType.DIRECT;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id")
    private User createdBy;

    @OneToMany(mappedBy = "thread", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<MessageThreadParticipant> participants = new ArrayList<>();

    @OneToMany(mappedBy = "thread", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("createdAt ASC")
    private List<Message> messages = new ArrayList<>();
}
