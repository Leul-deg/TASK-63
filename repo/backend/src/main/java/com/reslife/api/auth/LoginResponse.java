package com.reslife.api.auth;

import com.reslife.api.domain.user.User;
import com.reslife.api.security.ReslifeUserDetails;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Safe auth response — never includes the password hash or sensitive status fields.
 */
public record LoginResponse(
        UUID id,
        String username,
        String email,
        String firstName,
        String lastName,
        Set<String> roles
) {
    public static LoginResponse from(User user) {
        Set<String> roles = user.getUserRoles().stream()
                .map(ur -> ur.getRole().getName().name())
                .collect(Collectors.toSet());
        return new LoginResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                roles
        );
    }

    public static LoginResponse from(ReslifeUserDetails details, User user) {
        return from(user);
    }
}
