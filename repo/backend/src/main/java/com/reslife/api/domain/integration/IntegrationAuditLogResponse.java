package com.reslife.api.domain.integration;

import java.time.Instant;
import java.util.UUID;

public record IntegrationAuditLogResponse(
        UUID    id,
        String  keyId,
        String  keyName,
        IntegrationAuditLog.Direction direction,
        String  eventType,
        String  targetUrl,
        String  sourceIp,
        Integer httpStatus,
        boolean success,
        String  errorMessage,
        String  requestId,
        Instant createdAt
) {
    public static IntegrationAuditLogResponse from(IntegrationAuditLog log) {
        IntegrationKey key = log.getIntegrationKey();
        return new IntegrationAuditLogResponse(
                log.getId(),
                key != null ? key.getKeyId()  : null,
                key != null ? key.getName()   : null,
                log.getDirection(),
                log.getEventType(),
                log.getTargetUrl(),
                log.getSourceIp(),
                log.getHttpStatus(),
                log.isSuccess(),
                log.getErrorMessage(),
                log.getRequestId(),
                log.getCreatedAt()
        );
    }
}
