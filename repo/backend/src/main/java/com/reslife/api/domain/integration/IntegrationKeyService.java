package com.reslife.api.domain.integration;

import com.reslife.api.domain.user.UserService;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * CRUD and lifecycle management for integration keys and their webhook endpoints.
 */
@Service
@Transactional(readOnly = true)
public class IntegrationKeyService {

    private static final int         SECRET_BYTES = 32;
    private static final SecureRandom RANDOM       = new SecureRandom();

    private final IntegrationKeyRepository     keyRepo;
    private final WebhookEndpointRepository    webhookRepo;
    private final IntegrationAuditLogRepository auditRepo;
    private final LocalNetworkValidator         localNetworkValidator;
    private final UserService                   userService;

    public IntegrationKeyService(IntegrationKeyRepository keyRepo,
                                  WebhookEndpointRepository webhookRepo,
                                  IntegrationAuditLogRepository auditRepo,
                                  LocalNetworkValidator localNetworkValidator,
                                  UserService userService) {
        this.keyRepo               = keyRepo;
        this.webhookRepo           = webhookRepo;
        this.auditRepo             = auditRepo;
        this.localNetworkValidator = localNetworkValidator;
        this.userService           = userService;
    }

    // ── Keys ───────────────────────────────────────────────────────────────

    public Page<KeyResponse> listKeys(Pageable pageable) {
        return keyRepo.findAllByOrderByCreatedAtDesc(pageable).map(KeyResponse::from);
    }

    public KeyResponse getKey(UUID id) {
        return KeyResponse.from(findById(id));
    }

    /**
     * Creates a new integration key.  The plaintext secret is returned in the
     * response exactly once — it is never stored in plaintext and cannot be retrieved.
     */
    @Transactional
    public CreateKeyResponse createKey(CreateKeyRequest req, UUID actorId) {
        String plaintext = generateSecret();
        String keyId     = "rk_" + generateHex(16); // "rk_" + 32 hex chars

        IntegrationKey key = new IntegrationKey();
        key.setKeyId(keyId);
        key.setName(req.name());
        key.setDescription(req.description());
        key.setSecret(plaintext);                      // encrypted by StringEncryptionConverter
        key.setSecretPrefix(plaintext.substring(0, 8));
        key.setAllowedEvents(req.allowedEvents());
        if (actorId != null) {
            key.setCreatedBy(userService.findById(actorId));
        }
        keyRepo.save(key);

        return new CreateKeyResponse(
                key.getId(), key.getKeyId(), key.getName(), key.getDescription(),
                plaintext, key.getSecretPrefix(), key.getAllowedEvents(),
                key.isActive(), key.getCreatedAt());
    }

    @Transactional
    public KeyResponse updateKey(UUID id, CreateKeyRequest req) {
        IntegrationKey key = findById(id);
        key.setName(req.name());
        key.setDescription(req.description());
        key.setAllowedEvents(req.allowedEvents());
        return KeyResponse.from(keyRepo.save(key));
    }

    @Transactional
    public KeyResponse revokeKey(UUID id, String reason) {
        IntegrationKey key = findById(id);
        key.revoke(reason);
        return KeyResponse.from(keyRepo.save(key));
    }

    // ── Webhook endpoints ──────────────────────────────────────────────────

    public List<WebhookResponse> listWebhooks(UUID keyId) {
        return webhookRepo.findByIntegrationKeyIdOrderByCreatedAtDesc(keyId)
                .stream().map(WebhookResponse::from).toList();
    }

    /**
     * Registers a new outgoing webhook endpoint.  The plaintext signing secret
     * is returned once — it will not be retrievable afterwards.
     *
     * @throws IllegalArgumentException if the target URL does not resolve to a private address
     */
    @Transactional
    public CreateWebhookResponse addWebhook(UUID keyId, CreateWebhookRequest req) {
        localNetworkValidator.requireLocalTarget(req.targetUrl());

        IntegrationKey key           = findById(keyId);
        String         signingSecret = generateSecret();

        WebhookEndpoint endpoint = new WebhookEndpoint();
        endpoint.setIntegrationKey(key);
        endpoint.setName(req.name());
        endpoint.setTargetUrl(req.targetUrl());
        endpoint.setEventTypes(req.eventTypes());
        endpoint.setSigningSecret(signingSecret);
        endpoint.setSigningSecretPrefix(signingSecret.substring(0, 8));
        webhookRepo.save(endpoint);

        return new CreateWebhookResponse(
                endpoint.getId(), endpoint.getName(), endpoint.getTargetUrl(),
                endpoint.getEventTypes(), signingSecret, endpoint.getSigningSecretPrefix(),
                endpoint.isActive(), endpoint.getCreatedAt());
    }

    @Transactional
    public WebhookResponse toggleWebhook(UUID keyId, UUID webhookId, boolean active) {
        WebhookEndpoint endpoint = findWebhookForKey(keyId, webhookId);
        endpoint.setActive(active);
        return WebhookResponse.from(webhookRepo.save(endpoint));
    }

    @Transactional
    public void deleteWebhook(UUID keyId, UUID webhookId) {
        webhookRepo.delete(findWebhookForKey(keyId, webhookId));
    }

    // ── Audit logs ─────────────────────────────────────────────────────────

    public Page<IntegrationAuditLogResponse> getAuditLogs(UUID keyId, Pageable pageable) {
        return auditRepo.findByIntegrationKeyIdOrderByCreatedAtDesc(keyId, pageable)
                .map(IntegrationAuditLogResponse::from);
    }

    public Page<IntegrationAuditLogResponse> getAllAuditLogs(Pageable pageable) {
        return auditRepo.findAllByOrderByCreatedAtDesc(pageable)
                .map(IntegrationAuditLogResponse::from);
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private IntegrationKey findById(UUID id) {
        return keyRepo.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Integration key not found: " + id));
    }

    private WebhookEndpoint findWebhookForKey(UUID keyId, UUID webhookId) {
        return webhookRepo.findById(webhookId)
                .filter(w -> w.getIntegrationKey().getId().equals(keyId))
                .orElseThrow(() -> new EntityNotFoundException(
                        "Webhook endpoint " + webhookId + " not found for key " + keyId));
    }

    private static String generateSecret() {
        byte[] bytes = new byte[SECRET_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String generateHex(int bytes) {
        byte[] b = new byte[bytes];
        RANDOM.nextBytes(b);
        return HexFormat.of().formatHex(b);
    }
}
