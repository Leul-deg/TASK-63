package com.reslife.api.domain.notification;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Append-only audit record for a single acknowledgment action.
 *
 * <p>One row is inserted per (notification, user) pair when the recipient
 * clicks "I acknowledge receipt".  The UNIQUE constraint prevents duplicate
 * rows; calling acknowledge twice is a no-op at the service layer.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "notification_acknowledgments",
       uniqueConstraints = @UniqueConstraint(
               name = "uq_notif_ack_notification_user",
               columnNames = { "notification_id", "user_id" }))
public class NotificationAcknowledgment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "notification_id", nullable = false, updatable = false)
    private UUID notificationId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "session_id", length = 255, updatable = false)
    private String sessionId;

    @Column(name = "ip_address", length = 45, updatable = false)
    private String ipAddress;

    @Column(name = "acknowledged_at", nullable = false, updatable = false)
    private Instant acknowledgedAt = Instant.now();

    public NotificationAcknowledgment(UUID notificationId, UUID userId,
                                      String sessionId, String ipAddress) {
        this.notificationId = notificationId;
        this.userId         = userId;
        this.sessionId      = sessionId;
        this.ipAddress      = ipAddress;
        this.acknowledgedAt = Instant.now();
    }
}
