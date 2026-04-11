package com.reslife.api.domain.integration;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateKeyRequest(
        @NotBlank(message = "Name is required")
        @Size(max = 255, message = "Name must be 255 characters or fewer")
        String name,

        @Size(max = 1000, message = "Description must be 1000 characters or fewer")
        String description,

        /** JSON array of event types, e.g. {@code ["resident.created"]}. Null = unrestricted. */
        String allowedEvents
) {}
