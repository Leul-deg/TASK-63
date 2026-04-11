package com.reslife.api.domain.notification;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Staff-issued request to send a templated notification to one or more users.
 */
public record SendNotificationRequest(

        @NotBlank @Size(max = 100)
        String templateKey,

        @NotEmpty
        List<UUID> recipientIds,

        /** Template variable substitutions, e.g. {@code {"taskName": "Sign NDA", "dueDate": "2026-04-15"}}. */
        Map<String, String> variables,

        /** Optional override — uses template default if null. */
        String priority,

        /** Optional link to a related domain object. */
        String relatedEntityType,
        UUID   relatedEntityId
) {}
