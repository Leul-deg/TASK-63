package com.reslife.api.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Login payload. The {@code identifier} field accepts either a username or email.
 */
public record LoginRequest(
        @NotBlank @Size(max = 255) String identifier,
        @NotBlank @Size(max = 128) String password
) {}
