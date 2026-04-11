package com.reslife.api.domain.messaging;

import jakarta.validation.constraints.Size;

/**
 * Body for sending a text or quick-reply message.
 * Exactly one of {@code body} or {@code quickReplyKey} must be non-blank;
 * validated in the service layer.
 */
public record SendMessageRequest(
        @Size(max = 5000) String body,
        @Size(max = 100)  String quickReplyKey
) {}
