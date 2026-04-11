package com.reslife.api.domain.notification;

import com.reslife.api.domain.user.User;
import com.reslife.api.domain.user.UserService;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Manages creation, delivery, acknowledgment, and read-state of notifications.
 *
 * <h3>Delivery model</h3>
 * <p>Notifications are local-only: stored in PostgreSQL and polled / fetched
 * by the React frontend.  No email, push, or external delivery is performed.
 *
 * <h3>Template rendering</h3>
 * <p>Templates are loaded from the {@code notification_templates} table.
 * Variables use {@code {{key}}} placeholders; rendered text is stored on
 * the {@link Notification} record for immutable audit history.  The raw
 * variable map is also stored so the original substitution values are auditable.
 *
 * <h3>Acknowledgment</h3>
 * <p>When {@link Notification#isRequiresAcknowledgment()} is true the recipient
 * must explicitly call {@link #acknowledge}.  Each acknowledgment is recorded
 * in {@code notification_acknowledgments} with session and IP address for audit.
 */
@Service
@Transactional(readOnly = true)
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository             notifRepo;
    private final NotificationTemplateRepository     templateRepo;
    private final NotificationAcknowledgmentRepository ackRepo;
    private final UserService                        userService;

    public NotificationService(NotificationRepository notifRepo,
                               NotificationTemplateRepository templateRepo,
                               NotificationAcknowledgmentRepository ackRepo,
                               UserService userService) {
        this.notifRepo    = notifRepo;
        this.templateRepo = templateRepo;
        this.ackRepo      = ackRepo;
        this.userService  = userService;
    }

    // ── Inbox ──────────────────────────────────────────────────────────────────

    /**
     * Returns a paginated inbox for the recipient.
     *
     * @param unreadOnly  if true, returns only unread items
     * @param category    optional category filter
     * @param pageable    page/size/sort
     */
    public Page<Notification> findInbox(UUID recipientId, boolean unreadOnly,
                                        NotificationCategory category, Pageable pageable) {
        Boolean readFilter = unreadOnly ? Boolean.FALSE : null;
        return notifRepo.findInbox(recipientId, readFilter, category, pageable);
    }

    /** Counts unread and pending-acknowledgment totals for badge display. */
    public NotificationCountResponse counts(UUID recipientId) {
        long unread = notifRepo.countByRecipientIdAndReadFalse(recipientId);
        long pendingAck = notifRepo.countByRecipientIdAndRequiresAcknowledgmentTrueAndAcknowledgedAtIsNull(recipientId);
        return new NotificationCountResponse(unread, pendingAck);
    }

    public Notification findById(UUID id, UUID requesterId) {
        Notification n = notifRepo.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Notification not found: " + id));
        if (!n.getRecipient().getId().equals(requesterId)) {
            throw new EntityNotFoundException("Notification not found: " + id);
        }
        return n;
    }

    // ── Read state ─────────────────────────────────────────────────────────────

    @Transactional
    public NotificationResponse markRead(UUID notificationId, UUID recipientId) {
        Notification n = findById(notificationId, recipientId);
        if (!n.isRead()) {
            n.markRead();
            notifRepo.save(n);
        }
        return NotificationResponse.from(n);
    }

    @Transactional
    public int markAllRead(UUID recipientId) {
        return notifRepo.markAllReadForRecipient(recipientId);
    }

    // ── Acknowledgment ─────────────────────────────────────────────────────────

    /**
     * Records an acknowledgment for a notification that requires one.
     *
     * <p>Idempotent: if the recipient has already acknowledged, returns the
     * existing notification state without inserting a duplicate audit row.
     *
     * @param sessionId  Spring Session ID (for audit)
     * @param ipAddress  remote address (for audit)
     */
    @Transactional
    public NotificationResponse acknowledge(UUID notificationId, UUID recipientId,
                                            String sessionId, String ipAddress) {
        Notification n = findById(notificationId, recipientId);

        if (!n.isRequiresAcknowledgment()) {
            throw new IllegalArgumentException(
                    "Notification does not require acknowledgment.");
        }

        if (n.isAcknowledged()) {
            log.debug("Notification {} already acknowledged by {}", notificationId, recipientId);
            return NotificationResponse.from(n);
        }

        // Mark the notification itself
        n.markAcknowledged();
        notifRepo.save(n);

        // Insert immutable audit record
        NotificationAcknowledgment audit = new NotificationAcknowledgment(
                notificationId, recipientId, sessionId, ipAddress);
        ackRepo.save(audit);

        log.info("Notification {} acknowledged by user {} from {} (session {})",
                notificationId, recipientId, ipAddress, sessionId);
        return NotificationResponse.from(n);
    }

    // ── Template-based sending ─────────────────────────────────────────────────

    /**
     * Renders a template and sends the resulting notification to one or more recipients.
     *
     * @param templateKey   key of an active template in the database
     * @param recipientIds  one or more target users
     * @param variables     substitution map for {@code {{placeholder}}} tokens
     * @param priorityOverride if non-null, overrides the template's default priority
     * @param relatedEntityType optional related entity type (loose coupling)
     * @param relatedEntityId   optional related entity ID
     */
    @Transactional
    public List<Notification> sendFromTemplate(String templateKey,
                                               List<UUID> recipientIds,
                                               Map<String, String> variables,
                                               NotificationPriority priorityOverride,
                                               String relatedEntityType,
                                               UUID relatedEntityId) {
        NotificationTemplate template = templateRepo.findByTemplateKeyAndActiveTrue(templateKey)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No active template found for key: " + templateKey));

        String title = template.renderTitle(variables);
        String body  = template.renderBody(variables);
        NotificationPriority priority = priorityOverride != null
                ? priorityOverride : template.getDefaultPriority();

        return recipientIds.stream().map(recipientId -> {
            User recipient = userService.findById(recipientId);
            Notification n = new Notification();
            n.setRecipient(recipient);
            n.setTitle(title);
            n.setBody(body);
            n.setType(NotificationType.INFO);
            n.setPriority(priority);
            n.setCategory(template.getCategory());
            n.setRequiresAcknowledgment(template.isRequiresAcknowledgment());
            n.setTemplateKey(template.getTemplateKey());
            n.setVariables(variables);
            n.setRelatedEntityType(relatedEntityType);
            n.setRelatedEntityId(relatedEntityId);
            return notifRepo.save(n);
        }).toList();
    }

    /**
     * Convenience overload for sending to a single recipient without a related entity.
     */
    @Transactional
    public Notification sendFromTemplate(String templateKey, UUID recipientId,
                                         Map<String, String> variables) {
        return sendFromTemplate(templateKey, List.of(recipientId), variables, null, null, null)
                .getFirst();
    }

    // ── Ad-hoc sending (backwards-compatible) ─────────────────────────────────

    /**
     * Sends a plain notification without a template.  Retained for use by other
     * services that compose their own title/body.
     */
    @Transactional
    public Notification send(UUID recipientId, String title, String body,
                             NotificationType type) {
        User recipient = userService.findById(recipientId);
        Notification n = new Notification();
        n.setRecipient(recipient);
        n.setTitle(title);
        n.setBody(body);
        n.setType(type);
        return notifRepo.save(n);
    }

    @Transactional
    public Notification send(UUID recipientId, String title, String body,
                             NotificationType type,
                             String relatedEntityType, UUID relatedEntityId) {
        Notification n = send(recipientId, title, body, type);
        n.setRelatedEntityType(relatedEntityType);
        n.setRelatedEntityId(relatedEntityId);
        return notifRepo.save(n);
    }

    // ── Templates (staff view) ─────────────────────────────────────────────────

    public List<TemplateResponse> listTemplates() {
        return templateRepo.findByActiveTrueOrderByCategoryAscTemplateKeyAsc()
                .stream().map(TemplateResponse::from).toList();
    }
}
