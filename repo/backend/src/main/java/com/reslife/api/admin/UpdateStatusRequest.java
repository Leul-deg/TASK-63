package com.reslife.api.admin;

import com.reslife.api.domain.user.AccountStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request body for admin account-status change actions.
 */
public record UpdateStatusRequest(
        @NotNull AccountStatus status,
        @Size(max = 500) String reason
) {}
