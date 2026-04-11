package com.reslife.api.domain.integration;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

/**
 * Receives inbound events from authenticated local on-prem systems.
 *
 * <p>By the time a request reaches this controller, {@link IntegrationAuthFilter}
 * has already verified the HMAC signature, confirmed the timestamp is within the
 * 5-minute replay window, and checked the rate limit.  The resolved
 * {@link IntegrationKey} is available as a request attribute.
 *
 * <pre>
 * POST /api/integrations/events/{eventType}
 * </pre>
 */
@RestController
@RequestMapping("/api/integrations")
public class InboundController {

    private final IntegrationAuditLogRepository auditRepo;

    public InboundController(IntegrationAuditLogRepository auditRepo) {
        this.auditRepo = auditRepo;
    }

    /**
     * Accepts an inbound event from a local system and records it in the audit log.
     *
     * @param eventType dot-separated event identifier, e.g. {@code "device.checkin"}
     * @param body      raw JSON payload (optional — some events carry no body)
     */
    @PostMapping("/events/{eventType}")
    public InboundEventResponse receive(
            @PathVariable String eventType,
            @RequestBody(required = false) String body,
            HttpServletRequest request) {

        IntegrationKey key       = (IntegrationKey) request.getAttribute(IntegrationAuthFilter.KEY_ATTR);
        String         requestId = request.getHeader("X-Request-ID");
        String         sourceIp  = request.getRemoteAddr();

        IntegrationAuditLog entry = new IntegrationAuditLog();
        entry.setIntegrationKey(key);
        entry.setDirection(IntegrationAuditLog.Direction.INBOUND);
        entry.setEventType(eventType);
        entry.setSourceIp(sourceIp);
        entry.setHttpStatus(200);
        entry.setSuccess(true);
        entry.setRequestId(requestId);
        auditRepo.save(entry);

        return new InboundEventResponse("accepted", eventType, requestId, Instant.now());
    }
}
