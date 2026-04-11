package com.reslife.api.domain.messaging;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/** Staff-only request to send a system notice to one or more users. */
public record SendNoticeRequest(
        @NotBlank @Size(max = 255) String subject,
        @NotBlank @Size(max = 5000) String body,
        @NotEmpty List<UUID> recipientIds
) {}
