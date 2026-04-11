package com.reslife.api.domain.integration;

import java.time.Instant;

public record InboundEventResponse(
        String  status,
        String  eventType,
        String  requestId,
        Instant receivedAt
) {}
