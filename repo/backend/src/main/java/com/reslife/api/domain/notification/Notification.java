package com.reslife.api.domain.notification;

import com.reslife.api.common.BaseEntity;
import com.reslife.api.domain.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "notifications")
public class Notification extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipient_id", nullable = false)
    private User recipient;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private NotificationType type = NotificationType.INFO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationPriority priority = NotificationPriority.NORMAL;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private NotificationCategory category = NotificationCategory.GENERAL;

    /** When true, the recipient must explicitly acknowledge before dismissing. */
    @Column(name = "requires_acknowledgment", nullable = false)
    private boolean requiresAcknowledgment = false;

    @Column(name = "acknowledged_at")
    private Instant acknowledgedAt;

    /** Key of the template used to generate this notification (nullable for ad-hoc). */
    @Column(name = "template_key", length = 100)
    private String templateKey;

    /** JSON map of template variable values used when rendering this notification. */
    @Convert(converter = StringMapConverter.class)
    @Column(name = "variables", columnDefinition = "TEXT")
    private Map<String, String> variables;

    @Column(name = "is_read", nullable = false)
    private boolean read = false;

    @Column(name = "read_at")
    private Instant readAt;

    /** Loose coupling to any entity (e.g. "MoveInRecord", "HousingAgreement"). */
    @Column(name = "related_entity_type", length = 100)
    private String relatedEntityType;

    @Column(name = "related_entity_id")
    private UUID relatedEntityId;

    public void markRead() {
        this.read = true;
        this.readAt = Instant.now();
    }

    public void markAcknowledged() {
        this.acknowledgedAt = Instant.now();
        markRead(); // acknowledging also implicitly marks as read
    }

    public boolean isAcknowledged() {
        return acknowledgedAt != null;
    }
}
