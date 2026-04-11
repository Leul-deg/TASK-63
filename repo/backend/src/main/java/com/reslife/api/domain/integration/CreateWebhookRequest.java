package com.reslife.api.domain.integration;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateWebhookRequest(
        @NotBlank(message = "Name is required")
        @Size(max = 255, message = "Name must be 255 characters or fewer")
        String name,

        @NotBlank(message = "Target URL is required")
        @Size(max = 500, message = "Target URL must be 500 characters or fewer")
        String targetUrl,

        @NotBlank(message = "Event types are required")
        String eventTypes  // JSON array, e.g. ["resident.updated","booking.cancelled"]
) {}
