package com.reslife.api.domain.messaging;

import java.util.UUID;

public record QuickReplyResponse(UUID id, String replyKey, String label, String body) {
    public static QuickReplyResponse from(QuickReplyTemplate t) {
        return new QuickReplyResponse(t.getId(), t.getReplyKey(), t.getLabel(), t.getBody());
    }
}
