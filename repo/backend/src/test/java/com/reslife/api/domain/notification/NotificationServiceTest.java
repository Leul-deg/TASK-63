package com.reslife.api.domain.notification;

import com.reslife.api.domain.user.User;
import com.reslife.api.domain.user.UserService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link NotificationService} business logic.
 * All collaborators are mocked — no Spring context is loaded.
 */
class NotificationServiceTest {

    private final NotificationRepository               notifRepo    = mock(NotificationRepository.class);
    private final NotificationTemplateRepository       templateRepo = mock(NotificationTemplateRepository.class);
    private final NotificationAcknowledgmentRepository ackRepo      = mock(NotificationAcknowledgmentRepository.class);
    private final UserService                          userService  = mock(UserService.class);

    private NotificationService notificationService;

    private static final UUID RECIPIENT_ID    = UUID.randomUUID();
    private static final UUID NOTIFICATION_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService(notifRepo, templateRepo, ackRepo, userService);
    }

    // ── findInbox ─────────────────────────────────────────────────────────────

    @Test
    void findInbox_delegatesToRepository() {
        Notification stub = stubNotification(RECIPIENT_ID);
        Page<Notification> page = new PageImpl<>(List.of(stub));
        when(notifRepo.findInbox(eq(RECIPIENT_ID), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(page);

        Page<Notification> result = notificationService.findInbox(
                RECIPIENT_ID, false, null, Pageable.unpaged());

        assertThat(result.getContent()).hasSize(1);
        verify(notifRepo).findInbox(eq(RECIPIENT_ID), isNull(), isNull(), any(Pageable.class));
    }

    @Test
    void findInbox_passesUnreadFilterWhenRequested() {
        when(notifRepo.findInbox(eq(RECIPIENT_ID), eq(Boolean.FALSE), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        notificationService.findInbox(RECIPIENT_ID, true, null, Pageable.unpaged());

        verify(notifRepo).findInbox(eq(RECIPIENT_ID), eq(Boolean.FALSE), isNull(), any(Pageable.class));
    }

    // ── counts ────────────────────────────────────────────────────────────────

    @Test
    void counts_returnsAggregatedTotals() {
        when(notifRepo.countByRecipientIdAndReadFalse(RECIPIENT_ID)).thenReturn(3L);
        when(notifRepo.countByRecipientIdAndRequiresAcknowledgmentTrueAndAcknowledgedAtIsNull(RECIPIENT_ID))
                .thenReturn(1L);

        NotificationCountResponse result = notificationService.counts(RECIPIENT_ID);

        assertThat(result.unread()).isEqualTo(3L);
        assertThat(result.pendingAcknowledgment()).isEqualTo(1L);
    }

    // ── markRead ──────────────────────────────────────────────────────────────

    @Test
    void markRead_marksAndSavesUnreadNotification() {
        Notification n = stubNotification(RECIPIENT_ID);
        when(n.isRead()).thenReturn(false);
        when(notifRepo.findById(NOTIFICATION_ID)).thenReturn(Optional.of(n));
        when(notifRepo.save(n)).thenReturn(n);

        notificationService.markRead(NOTIFICATION_ID, RECIPIENT_ID);

        verify(n).markRead();
        verify(notifRepo).save(n);
    }

    @Test
    void markRead_skipsWriteIfAlreadyRead() {
        Notification n = stubNotification(RECIPIENT_ID);
        when(n.isRead()).thenReturn(true);
        when(notifRepo.findById(NOTIFICATION_ID)).thenReturn(Optional.of(n));

        notificationService.markRead(NOTIFICATION_ID, RECIPIENT_ID);

        verify(n, never()).markRead();
        verify(notifRepo, never()).save(any());
    }

    @Test
    void markRead_throwsWhenNotificationBelongsToDifferentRecipient() {
        User otherUser = mock(User.class);
        when(otherUser.getId()).thenReturn(UUID.randomUUID());
        Notification other = mock(Notification.class);
        when(other.getRecipient()).thenReturn(otherUser);
        when(notifRepo.findById(NOTIFICATION_ID)).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> notificationService.markRead(NOTIFICATION_ID, RECIPIENT_ID))
                .isInstanceOf(EntityNotFoundException.class);
    }

    // ── markAllRead ───────────────────────────────────────────────────────────

    @Test
    void markAllRead_delegatesAndReturnsBulkCount() {
        when(notifRepo.markAllReadForRecipient(RECIPIENT_ID)).thenReturn(7);

        int count = notificationService.markAllRead(RECIPIENT_ID);

        assertThat(count).isEqualTo(7);
    }

    // ── acknowledge ───────────────────────────────────────────────────────────

    @Test
    void acknowledge_idempotentWhenAlreadyAcknowledged() {
        Notification n = stubNotification(RECIPIENT_ID);
        when(n.isRequiresAcknowledgment()).thenReturn(true);
        when(n.isAcknowledged()).thenReturn(true);
        when(notifRepo.findById(NOTIFICATION_ID)).thenReturn(Optional.of(n));

        notificationService.acknowledge(NOTIFICATION_ID, RECIPIENT_ID, "sess", "127.0.0.1");

        verify(ackRepo, never()).save(any());
        verify(notifRepo, never()).save(any());
    }

    @Test
    void acknowledge_throwsWhenAcknowledgmentNotRequired() {
        Notification n = stubNotification(RECIPIENT_ID);
        when(n.isRequiresAcknowledgment()).thenReturn(false);
        when(notifRepo.findById(NOTIFICATION_ID)).thenReturn(Optional.of(n));

        assertThatThrownBy(() ->
                notificationService.acknowledge(NOTIFICATION_ID, RECIPIENT_ID, "sess", "127.0.0.1"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void acknowledge_savesAuditRecordOnFirstAcknowledgment() {
        Notification n = stubNotification(RECIPIENT_ID);
        when(n.isRequiresAcknowledgment()).thenReturn(true);
        when(n.isAcknowledged()).thenReturn(false);
        when(notifRepo.findById(NOTIFICATION_ID)).thenReturn(Optional.of(n));
        when(notifRepo.save(n)).thenReturn(n);
        when(ackRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        notificationService.acknowledge(NOTIFICATION_ID, RECIPIENT_ID, "sess123", "10.0.0.1");

        verify(n).markAcknowledged();
        verify(notifRepo).save(n);
        verify(ackRepo).save(any(NotificationAcknowledgment.class));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Notification stubNotification(UUID recipientId) {
        User recipient = mock(User.class);
        when(recipient.getId()).thenReturn(recipientId);

        Notification n = mock(Notification.class);
        when(n.getId()).thenReturn(NOTIFICATION_ID);
        when(n.getRecipient()).thenReturn(recipient);
        when(n.getTitle()).thenReturn("Test");
        when(n.getBody()).thenReturn("Body");
        when(n.getType()).thenReturn(NotificationType.INFO);
        when(n.getPriority()).thenReturn(NotificationPriority.NORMAL);
        when(n.getCategory()).thenReturn(NotificationCategory.GENERAL);
        when(n.isRead()).thenReturn(false);
        when(n.isRequiresAcknowledgment()).thenReturn(false);
        when(n.isAcknowledged()).thenReturn(false);
        when(n.getCreatedAt()).thenReturn(Instant.now());
        when(n.getReadAt()).thenReturn(null);
        return n;
    }
}
