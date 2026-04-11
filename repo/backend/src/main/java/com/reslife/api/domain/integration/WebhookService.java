package com.reslife.api.domain.integration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Dispatches outgoing webhook events to all active, subscribed local-network endpoints.
 *
 * <h3>Signing</h3>
 * <p>Every outgoing request is signed with the endpoint's dedicated signing secret.
 * The receiving system verifies the signature using the same HMAC-SHA256 scheme
 * (see {@code INTEGRATION.md} for the full spec).
 *
 * <h3>Request headers</h3>
 * <pre>
 *   X-Reslife-Signature: sha256={hex}   — HMAC-SHA256 of "{epoch}\n{body}"
 *   X-Reslife-Timestamp: {epoch}         — Unix seconds
 *   X-Reslife-Event:     {eventType}
 *   X-Reslife-Delivery:  {uuid}          — unique per attempt
 * </pre>
 */
@Service
public class WebhookService {

    private static final Logger log = LoggerFactory.getLogger(WebhookService.class);

    private final WebhookEndpointRepository    endpointRepo;
    private final WebhookDeliveryRepository    deliveryRepo;
    private final IntegrationAuditLogRepository auditRepo;
    private final HmacService                  hmacService;
    private final ObjectMapper                 objectMapper;
    private final RestTemplate                 restTemplate;
    private final LocalNetworkValidator        localNetworkValidator;

    public WebhookService(WebhookEndpointRepository endpointRepo,
                          WebhookDeliveryRepository deliveryRepo,
                          IntegrationAuditLogRepository auditRepo,
                          HmacService hmacService,
                          ObjectMapper objectMapper,
                          @Qualifier("webhookRestTemplate") RestTemplate restTemplate,
                          LocalNetworkValidator localNetworkValidator) {
        this.endpointRepo = endpointRepo;
        this.deliveryRepo = deliveryRepo;
        this.auditRepo    = auditRepo;
        this.hmacService  = hmacService;
        this.objectMapper = objectMapper;
        this.restTemplate = restTemplate;
        this.localNetworkValidator = localNetworkValidator;
    }

    /**
     * Dispatches {@code eventType} to every active endpoint whose subscription list
     * contains that event.  Each delivery is attempted synchronously and recorded
     * regardless of outcome.
     *
     * @param eventType dot-separated event identifier, e.g. {@code "resident.updated"}
     * @param payload   object to serialize as JSON and send as the request body
     */
    @Transactional
    public void dispatch(String eventType, Object payload) {
        // Query with quoted form so "resident.updated" doesn't match "resident.updated_extra"
        String pattern = "\"" + eventType + "\"";
        List<WebhookEndpoint> targets =
                endpointRepo.findByActiveTrueAndIntegrationKeyActiveTrueAndEventTypesContaining(pattern);
        if (targets.isEmpty()) return;

        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            log.error("Cannot serialize webhook payload for event {}: {}", eventType, e.getMessage());
            return;
        }

        for (WebhookEndpoint endpoint : targets) {
            deliverOne(endpoint, eventType, json);
        }
    }

    // ── Private ────────────────────────────────────────────────────────────

    private void deliverOne(WebhookEndpoint endpoint, String eventType, String json) {
        String deliveryId = UUID.randomUUID().toString();
        long   timestamp  = Instant.now().getEpochSecond();
        String signature  = hmacService.sign(endpoint.getSigningSecret(), timestamp, json.getBytes());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Reslife-Signature", signature);
        headers.set("X-Reslife-Timestamp", String.valueOf(timestamp));
        headers.set("X-Reslife-Event",     eventType);
        headers.set("X-Reslife-Delivery",  deliveryId);

        WebhookDelivery      delivery   = new WebhookDelivery();
        IntegrationAuditLog  auditEntry = new IntegrationAuditLog();

        delivery.setWebhookEndpoint(endpoint);
        delivery.setEventType(eventType);
        delivery.setPayload(json);

        auditEntry.setIntegrationKey(endpoint.getIntegrationKey());
        auditEntry.setDirection(IntegrationAuditLog.Direction.OUTBOUND);
        auditEntry.setEventType(eventType);
        auditEntry.setTargetUrl(endpoint.getTargetUrl());
        auditEntry.setRequestId(deliveryId);

        try {
            // Revalidate locality at send time so a hostname cannot drift from a private
            // address at registration time to a public address later.
            localNetworkValidator.requireLocalTarget(endpoint.getTargetUrl());

            ResponseEntity<String> response = restTemplate.postForEntity(
                    endpoint.getTargetUrl(),
                    new HttpEntity<>(json, headers),
                    String.class);

            int status = response.getStatusCode().value();
            boolean ok = response.getStatusCode().is2xxSuccessful();

            delivery.setHttpStatus(status);
            delivery.setResponseBody(truncate(response.getBody(), 4000));
            delivery.setSuccess(ok);
            if (ok) delivery.setDeliveredAt(Instant.now());

            auditEntry.setHttpStatus(status);
            auditEntry.setSuccess(ok);
            if (!ok) auditEntry.setErrorMessage("Non-2xx response: " + status);

            log.info("Webhook [{}] {} → {} HTTP {}", eventType, deliveryId, endpoint.getTargetUrl(), status);

        } catch (HttpStatusCodeException e) {
            int status = e.getStatusCode().value();
            delivery.setHttpStatus(status);
            delivery.setResponseBody(truncate(e.getResponseBodyAsString(), 4000));
            delivery.setSuccess(false);
            delivery.setErrorMessage(e.getMessage());
            auditEntry.setHttpStatus(status);
            auditEntry.setSuccess(false);
            auditEntry.setErrorMessage(e.getMessage());
            log.warn("Webhook [{}] {} → {} failed with HTTP {}", eventType, deliveryId, endpoint.getTargetUrl(), status);

        } catch (Exception e) {
            delivery.setSuccess(false);
            delivery.setErrorMessage(e.getMessage());
            auditEntry.setSuccess(false);
            auditEntry.setErrorMessage(e.getMessage());
            log.warn("Webhook [{}] {} → {} error: {}", eventType, deliveryId, endpoint.getTargetUrl(), e.getMessage());
        }

        deliveryRepo.save(delivery);
        auditRepo.save(auditEntry);
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() > max ? s.substring(0, max) : s;
    }
}
