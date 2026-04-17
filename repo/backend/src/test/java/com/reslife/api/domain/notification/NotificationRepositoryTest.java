package com.reslife.api.domain.notification;

import com.reslife.api.domain.user.AccountStatus;
import com.reslife.api.domain.user.User;
import com.reslife.api.domain.user.UserRepository;
import com.reslife.api.encryption.EncryptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.test.context.TestPropertySource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JPA-layer tests for {@link NotificationRepository}.
 *
 * <p>The {@code findInbox} query contains non-trivial JPQL ordering (unread first,
 * CRITICAL priority surfaced above NORMAL, requiresAcknowledgment items boosted).
 * These tests confirm that the custom JPQL and the two {@code @Modifying} queries
 * produce the expected SQL against a real H2 schema.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:reslife_notif;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DEFAULT_NULL_ORDERING=HIGH",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "app.encryption.key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
    "spring.session.store-type=none",
    "spring.jpa.show-sql=false"
})
@Import(EncryptionService.class)
class NotificationRepositoryTest {

    @Autowired private NotificationRepository notifRepo;
    @Autowired private UserRepository         userRepo;
    @Autowired private TestEntityManager      em;

    private User recipient;

    @BeforeEach
    void setUp() {
        User u = new User();
        u.setUsername("notif-user-" + UUID.randomUUID());
        u.setEmail(UUID.randomUUID() + "@test.com");
        u.setPasswordHash("hash");
        u.setAccountStatus(AccountStatus.ACTIVE);
        recipient = em.persistAndFlush(u);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Notification notif(NotificationPriority priority, boolean read, boolean requiresAck) {
        Notification n = new Notification();
        n.setRecipient(recipient);
        n.setTitle(priority + " notification");
        n.setBody("Body");
        n.setPriority(priority);
        n.setRead(read);
        n.setRequiresAcknowledgment(requiresAck);
        return em.persistAndFlush(n);
    }

    // ── countByRecipientIdAndReadFalse ────────────────────────────────────────

    @Test
    void countUnread_returnsZero_whenNoNotifications() {
        assertThat(notifRepo.countByRecipientIdAndReadFalse(recipient.getId())).isZero();
    }

    @Test
    void countUnread_countsOnlyUnreadNotifications() {
        notif(NotificationPriority.NORMAL,   false, false); // unread
        notif(NotificationPriority.NORMAL,   false, false); // unread
        notif(NotificationPriority.HIGH,     true,  false); // read — not counted
        em.flush();

        assertThat(notifRepo.countByRecipientIdAndReadFalse(recipient.getId())).isEqualTo(2);
    }

    // ── countByRecipientIdAndRequiresAcknowledgmentTrueAndAcknowledgedAtIsNull ──

    @Test
    void countPendingAck_returnsZero_whenAllAcknowledged() {
        assertThat(notifRepo
                .countByRecipientIdAndRequiresAcknowledgmentTrueAndAcknowledgedAtIsNull(
                        recipient.getId())).isZero();
    }

    @Test
    void countPendingAck_countsNotificationsRequiringAcknowledgmentWithNoTimestamp() {
        notif(NotificationPriority.CRITICAL, false, true);  // requires ack, not done
        notif(NotificationPriority.HIGH,     false, false); // no ack required
        em.flush();

        assertThat(notifRepo
                .countByRecipientIdAndRequiresAcknowledgmentTrueAndAcknowledgedAtIsNull(
                        recipient.getId())).isEqualTo(1);
    }

    // ── findInbox ordering ────────────────────────────────────────────────────

    @Test
    void findInbox_returnsAllNotificationsForRecipient() {
        notif(NotificationPriority.NORMAL, false, false);
        notif(NotificationPriority.HIGH,   false, false);
        em.flush();

        Page<Notification> page = notifRepo.findInbox(
                recipient.getId(), null, null, PageRequest.of(0, 20));
        assertThat(page.getTotalElements()).isEqualTo(2);
    }

    @Test
    void findInbox_surfacesCriticalAboveNormal_whenBothUnread() {
        notif(NotificationPriority.NORMAL,   false, false);
        notif(NotificationPriority.CRITICAL, false, false);
        em.flush();

        Page<Notification> page = notifRepo.findInbox(
                recipient.getId(), null, null, PageRequest.of(0, 20));
        assertThat(page.getContent().get(0).getPriority()).isEqualTo(NotificationPriority.CRITICAL);
    }

    @Test
    void findInbox_unreadFirstFilter_returnsOnlyUnread() {
        notif(NotificationPriority.NORMAL, false, false); // unread
        notif(NotificationPriority.NORMAL, true,  false); // read
        em.flush();

        Page<Notification> page = notifRepo.findInbox(
                recipient.getId(), false, null, PageRequest.of(0, 20));
        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().get(0).isRead()).isFalse();
    }

    @Test
    void findInbox_categoryFilter_returnsOnlyMatchingCategory() {
        Notification settlement = notif(NotificationPriority.NORMAL, false, false);
        settlement.setCategory(NotificationCategory.SETTLEMENT);
        em.persistAndFlush(settlement);
        notif(NotificationPriority.NORMAL, false, false); // GENERAL category
        em.flush();

        Page<Notification> page = notifRepo.findInbox(
                recipient.getId(), null, NotificationCategory.SETTLEMENT, PageRequest.of(0, 20));
        assertThat(page.getTotalElements()).isEqualTo(1);
    }

    // ── markAllReadForRecipient ────────────────────────────────────────────────

    @Test
    void markAllReadForRecipient_updatesAllUnreadToRead() {
        notif(NotificationPriority.NORMAL, false, false);
        notif(NotificationPriority.HIGH,   false, false);
        em.flush();

        int updated = notifRepo.markAllReadForRecipient(recipient.getId());
        em.flush();
        em.clear();

        assertThat(updated).isEqualTo(2);
        assertThat(notifRepo.countByRecipientIdAndReadFalse(recipient.getId())).isZero();
    }

    @Test
    void markAllReadForRecipient_returnsZero_whenAllAlreadyRead() {
        notif(NotificationPriority.NORMAL, true, false);
        em.flush();

        int updated = notifRepo.markAllReadForRecipient(recipient.getId());
        assertThat(updated).isZero();
    }

    @TestConfiguration
    @EnableJpaAuditing
    static class AuditingConfig {}
}
