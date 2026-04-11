package com.reslife.api.admin;

import com.reslife.api.domain.user.AccountStatus;
import com.reslife.api.domain.user.User;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Lightweight user projection for admin account-governance screens.
 * Includes status and purge-timing fields that are omitted from the
 * standard {@link com.reslife.api.auth.LoginResponse}.
 */
public record UserSummaryDto(
        UUID id,
        String username,
        String email,
        String firstName,
        String lastName,
        AccountStatus accountStatus,
        String statusReason,
        Instant scheduledPurgeAt,
        boolean deleted,
        Instant createdAt,
        Set<String> roles
) {
    public static UserSummaryDto from(User user) {
        Set<String> roles = user.getUserRoles().stream()
                .map(ur -> ur.getRole().getName().name())
                .collect(Collectors.toSet());
        return new UserSummaryDto(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getAccountStatus(),
                user.getStatusReason(),
                user.getScheduledPurgeAt(),
                user.isDeleted(),
                user.getCreatedAt(),
                roles
        );
    }
}
