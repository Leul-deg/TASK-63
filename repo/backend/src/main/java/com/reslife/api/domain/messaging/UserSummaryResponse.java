package com.reslife.api.domain.messaging;

import com.reslife.api.domain.user.User;

import java.util.UUID;

public record UserSummaryResponse(UUID id, String username, String displayName) {
    public static UserSummaryResponse from(User u) {
        String display = (u.getFirstName() != null && u.getLastName() != null)
                ? u.getFirstName() + " " + u.getLastName()
                : u.getUsername();
        return new UserSummaryResponse(u.getId(), u.getUsername(), display);
    }
}
