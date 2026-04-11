package com.reslife.api.domain.notification;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record NotificationResponse(
        UUID                 id,
        String               title,
        String               body,
        String               type,
        String               priority,
        String               category,
        boolean              read,
        Instant              readAt,
        boolean              requiresAcknowledgment,
        boolean              acknowledged,
        Instant              acknowledgedAt,
        String               templateKey,
        Map<String, String>  variables,
        String               relatedEntityType,
        UUID                 relatedEntityId,
        Instant              createdAt
) {
    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(
                n.getId(),
                n.getTitle(),
                n.getBody(),
                n.getType().name(),
                n.getPriority().name(),
                n.getCategory().name(),
                n.isRead(),
                n.getReadAt(),
                n.isRequiresAcknowledgment(),
                n.isAcknowledged(),
                n.getAcknowledgedAt(),
                n.getTemplateKey(),
                n.getVariables(),
                n.getRelatedEntityType(),
                n.getRelatedEntityId(),
                n.getCreatedAt()
        );
    }
}
