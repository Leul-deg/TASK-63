package com.reslife.api.domain.messaging;

import com.reslife.api.domain.user.User;

import java.util.UUID;

public record ParticipantInfo(
        UUID   userId,
        String username,
        String displayName
) {
    public static ParticipantInfo from(User u) {
        String display = (u.getFirstName() != null && u.getLastName() != null)
                ? u.getFirstName() + " " + u.getLastName()
                : u.getUsername();
        return new ParticipantInfo(u.getId(), u.getUsername(), display);
    }
}
