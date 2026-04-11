package com.reslife.api.domain.integration;

import jakarta.validation.constraints.Size;

public record RevokeKeyRequest(
        @Size(max = 500, message = "Reason must be 500 characters or fewer")
        String reason
) {}
