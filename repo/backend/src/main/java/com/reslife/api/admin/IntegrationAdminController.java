package com.reslife.api.admin;

import com.reslife.api.domain.integration.*;
import com.reslife.api.security.ReslifeUserDetails;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Admin-only REST endpoints for managing integration keys and webhook endpoints.
 *
 * <pre>
 * GET    /api/admin/integration-keys                              — list all keys (paged)
 * POST   /api/admin/integration-keys                              — create key
 * GET    /api/admin/integration-keys/{id}                         — get single key
 * PUT    /api/admin/integration-keys/{id}                         — update name/description/events
 * POST   /api/admin/integration-keys/{id}/revoke                  — revoke key
 *
 * GET    /api/admin/integration-keys/{id}/webhooks                — list webhooks
 * POST   /api/admin/integration-keys/{id}/webhooks                — add webhook endpoint
 * POST   /api/admin/integration-keys/{id}/webhooks/{wid}/toggle   — enable/disable
 * DELETE /api/admin/integration-keys/{id}/webhooks/{wid}          — delete webhook
 *
 * GET    /api/admin/integration-keys/{id}/audit                   — audit log for one key
 * GET    /api/admin/integrations/audit                            — all integration audit logs
 * </pre>
 */
@RestController
@RequestMapping("/api/admin")
public class IntegrationAdminController {

    private final IntegrationKeyService keyService;

    public IntegrationAdminController(IntegrationKeyService keyService) {
        this.keyService = keyService;
    }

    // ── Keys ───────────────────────────────────────────────────────────────

    @GetMapping("/integration-keys")
    public Page<KeyResponse> list(@PageableDefault(size = 20) Pageable pageable) {
        return keyService.listKeys(pageable);
    }

    @PostMapping("/integration-keys")
    @ResponseStatus(HttpStatus.CREATED)
    public CreateKeyResponse create(
            @Valid @RequestBody CreateKeyRequest req,
            @AuthenticationPrincipal ReslifeUserDetails principal) {
        return keyService.createKey(req, principal.getUserId());
    }

    @GetMapping("/integration-keys/{id}")
    public KeyResponse get(@PathVariable UUID id) {
        return keyService.getKey(id);
    }

    @PutMapping("/integration-keys/{id}")
    public KeyResponse update(@PathVariable UUID id,
                               @Valid @RequestBody CreateKeyRequest req) {
        return keyService.updateKey(id, req);
    }

    @PostMapping("/integration-keys/{id}/revoke")
    public KeyResponse revoke(@PathVariable UUID id,
                               @Valid @RequestBody RevokeKeyRequest req) {
        return keyService.revokeKey(id, req.reason());
    }

    // ── Webhook endpoints ──────────────────────────────────────────────────

    @GetMapping("/integration-keys/{id}/webhooks")
    public List<WebhookResponse> listWebhooks(@PathVariable UUID id) {
        return keyService.listWebhooks(id);
    }

    @PostMapping("/integration-keys/{id}/webhooks")
    @ResponseStatus(HttpStatus.CREATED)
    public CreateWebhookResponse addWebhook(@PathVariable UUID id,
                                             @Valid @RequestBody CreateWebhookRequest req) {
        return keyService.addWebhook(id, req);
    }

    @PostMapping("/integration-keys/{id}/webhooks/{wid}/toggle")
    public WebhookResponse toggleWebhook(@PathVariable UUID id,
                                          @PathVariable UUID wid,
                                          @RequestParam boolean active) {
        return keyService.toggleWebhook(id, wid, active);
    }

    @DeleteMapping("/integration-keys/{id}/webhooks/{wid}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteWebhook(@PathVariable UUID id, @PathVariable UUID wid) {
        keyService.deleteWebhook(id, wid);
    }

    // ── Audit logs ─────────────────────────────────────────────────────────

    @GetMapping("/integration-keys/{id}/audit")
    public Page<IntegrationAuditLogResponse> auditForKey(
            @PathVariable UUID id,
            @PageableDefault(size = 50) Pageable pageable) {
        return keyService.getAuditLogs(id, pageable);
    }

    @GetMapping("/integrations/audit")
    public Page<IntegrationAuditLogResponse> allAudit(
            @PageableDefault(size = 50) Pageable pageable) {
        return keyService.getAllAuditLogs(pageable);
    }
}
