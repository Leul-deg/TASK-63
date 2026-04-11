package com.reslife.api.domain.notification;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface NotificationAcknowledgmentRepository
        extends JpaRepository<NotificationAcknowledgment, UUID> {

    boolean existsByNotificationIdAndUserId(UUID notificationId, UUID userId);

    Optional<NotificationAcknowledgment> findByNotificationIdAndUserId(
            UUID notificationId, UUID userId);
}
