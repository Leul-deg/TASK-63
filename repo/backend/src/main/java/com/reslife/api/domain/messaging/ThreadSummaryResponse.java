package com.reslife.api.domain.messaging;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ThreadSummaryResponse(
        UUID                  id,
        String                subject,
        String                threadType,
        List<ParticipantInfo> participants,
        MessageResponse       lastMessage,
        long                  unreadCount,
        Instant               updatedAt
) {}
