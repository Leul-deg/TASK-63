package com.reslife.api.domain.notification;

import com.reslife.api.security.ReslifeUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST endpoints for the notification centre.
 *
 * <pre>
 * GET  /api/notifications                 — inbox (params: unreadOnly, category, page, size)
 * GET  /api/notifications/count           — { unread, pendingAcknowledgment }
 * GET  /api/notifications/{id}            — single notification
 * POST /api/notifications/{id}/read       — mark one as read
 * POST /api/notifications/read-all        — mark all as read
 * POST /api/notifications/{id}/acknowledge — record acknowledgment + audit
 *
 * GET  /api/notifications/templates       — list active templates (staff+)
 * POST /api/notifications/send            — send via template  (staff+)
 * </pre>
 *
 * <p>All write endpoints are authenticated.  Template listing and sending
 * are restricted to staff roles via {@code @PreAuthorize}.
 */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private static final String STAFF_ROLES =
            "hasAnyRole('ADMIN','HOUSING_ADMINISTRATOR','DIRECTOR'," +
            "'RESIDENT_DIRECTOR','RESIDENT_ASSISTANT','RESIDENCE_STAFF','STAFF')";

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    // ── Inbox ──────────────────────────────────────────────────────────────────

    @GetMapping
    public Page<NotificationResponse> inbox(
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal ReslifeUserDetails principal) {

        NotificationCategory cat = null;
        if (category != null && !category.isBlank()) {
            try { cat = NotificationCategory.valueOf(category.toUpperCase()); }
            catch (IllegalArgumentException ignored) {}
        }

        Pageable pageable = PageRequest.of(page, Math.min(size, 100));
        return notificationService
                .findInbox(principal.getUserId(), unreadOnly, cat, pageable)
                .map(NotificationResponse::from);
    }

    // ── Counts for badge display ───────────────────────────────────────────────

    @GetMapping("/count")
    public NotificationCountResponse count(
            @AuthenticationPrincipal ReslifeUserDetails principal) {
        return notificationService.counts(principal.getUserId());
    }

    // ── Single notification ────────────────────────────────────────────────────

    @GetMapping("/{id}")
    public NotificationResponse getOne(
            @PathVariable UUID id,
            @AuthenticationPrincipal ReslifeUserDetails principal) {
        return NotificationResponse.from(
                notificationService.findById(id, principal.getUserId()));
    }

    // ── Mark read ──────────────────────────────────────────────────────────────

    @PostMapping("/{id}/read")
    public NotificationResponse markRead(
            @PathVariable UUID id,
            @AuthenticationPrincipal ReslifeUserDetails principal) {
        return notificationService.markRead(id, principal.getUserId());
    }

    @PostMapping("/read-all")
    public Map<String, Integer> markAllRead(
            @AuthenticationPrincipal ReslifeUserDetails principal) {
        int count = notificationService.markAllRead(principal.getUserId());
        return Map.of("marked", count);
    }

    // ── Acknowledgment ─────────────────────────────────────────────────────────

    /**
     * Records that the recipient has explicitly acknowledged a critical notification.
     * Also marks the notification as read.  Idempotent.
     */
    @PostMapping("/{id}/acknowledge")
    public NotificationResponse acknowledge(
            @PathVariable UUID id,
            @AuthenticationPrincipal ReslifeUserDetails principal,
            HttpSession session,
            HttpServletRequest request) {
        return notificationService.acknowledge(
                id,
                principal.getUserId(),
                session.getId(),
                request.getRemoteAddr()
        );
    }

    // ── Templates (staff only) ─────────────────────────────────────────────────

    @GetMapping("/templates")
    @PreAuthorize(STAFF_ROLES)
    public List<TemplateResponse> listTemplates() {
        return notificationService.listTemplates();
    }

    // ── Send via template (staff only) ────────────────────────────────────────

    @PostMapping("/send")
    @PreAuthorize(STAFF_ROLES)
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Integer> send(
            @Valid @RequestBody SendNotificationRequest req) {

        NotificationPriority priority = null;
        if (req.priority() != null) {
            try { priority = NotificationPriority.valueOf(req.priority().toUpperCase()); }
            catch (IllegalArgumentException ignored) {}
        }

        List<?> sent = notificationService.sendFromTemplate(
                req.templateKey(),
                req.recipientIds(),
                req.variables() != null ? req.variables() : Map.of(),
                priority,
                req.relatedEntityType(),
                req.relatedEntityId()
        );
        return Map.of("sent", sent.size());
    }
}
