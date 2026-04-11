package com.reslife.api.domain.messaging;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Pre-seeded quick-reply templates available to staff when composing messages.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "quick_reply_templates")
public class QuickReplyTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "reply_key", nullable = false, unique = true, length = 100)
    private String replyKey;

    @Column(nullable = false, length = 100)
    private String label;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;
}
