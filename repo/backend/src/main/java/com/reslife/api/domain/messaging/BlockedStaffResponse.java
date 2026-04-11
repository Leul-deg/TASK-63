package com.reslife.api.domain.messaging;

import java.time.Instant;
import java.util.UUID;

public record BlockedStaffResponse(UUID staffUserId, String displayName, Instant blockedAt) {}
