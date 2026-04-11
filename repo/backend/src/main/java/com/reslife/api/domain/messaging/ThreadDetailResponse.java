package com.reslife.api.domain.messaging;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ThreadDetailResponse(
        UUID                  id,
        String                subject,
        String                threadType,
        List<ParticipantInfo> participants,
        List<MessageResponse> messages,
        Instant               updatedAt
) {}
