package com.reslife.api.domain.messaging;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record CreateThreadRequest(
        @Size(max = 255) String subject,
        @NotEmpty List<UUID> recipientIds,
        @NotBlank @Size(max = 5000) String body
) {}
