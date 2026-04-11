package com.reslife.api.domain.notification;

import java.util.UUID;

public record TemplateResponse(
        UUID    id,
        String  templateKey,
        String  category,
        String  titlePattern,
        String  bodyPattern,
        String  defaultPriority,
        boolean requiresAcknowledgment,
        String  description
) {
    public static TemplateResponse from(NotificationTemplate t) {
        return new TemplateResponse(
                t.getId(), t.getTemplateKey(), t.getCategory().name(),
                t.getTitlePattern(), t.getBodyPattern(),
                t.getDefaultPriority().name(), t.isRequiresAcknowledgment(),
                t.getDescription()
        );
    }
}
