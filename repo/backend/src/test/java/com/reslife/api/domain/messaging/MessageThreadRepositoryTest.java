package com.reslife.api.domain.messaging;

import com.reslife.api.domain.user.AccountStatus;
import com.reslife.api.domain.user.User;
import com.reslife.api.domain.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.test.context.TestPropertySource;

import com.reslife.api.encryption.EncryptionService;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JPA-layer tests for {@link MessageThreadRepository}.
 *
 * <p>Validates the participant-based thread lookup, the membership check, and
 * the {@code touchUpdatedAt} update that drives inbox ordering.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:reslife_msgthread;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DEFAULT_NULL_ORDERING=HIGH",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "app.encryption.key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
    "spring.session.store-type=none",
    "spring.jpa.show-sql=false"
})
@Import(EncryptionService.class)
class MessageThreadRepositoryTest {

    @Autowired private MessageThreadRepository threadRepo;
    @Autowired private UserRepository          userRepo;
    @Autowired private TestEntityManager       em;

    // ── Helpers ───────────────────────────────────────────────────────────────

    private User savedUser(String email, String username) {
        User u = new User();
        u.setEmail(email);
        u.setUsername(username);
        u.setPasswordHash("$2a$10$hash");
        u.setFirstName("First");
        u.setLastName("Last");
        u.setAccountStatus(AccountStatus.ACTIVE);
        return em.persistAndFlush(u);
    }

    private MessageThread savedThread(String subject) {
        MessageThread t = new MessageThread();
        t.setSubject(subject);
        return em.persistAndFlush(t);
    }

    private void addParticipant(MessageThread thread, User user) {
        MessageThreadParticipant p = new MessageThreadParticipant(thread, user);
        em.persistAndFlush(p);
    }

    // ── findByParticipantUserId ───────────────────────────────────────────────

    @Test
    void findByParticipantUserId_returnsThreads_whereUserIsParticipant() {
        User user   = savedUser("part@test.com", "partuser");
        User other  = savedUser("other@test.com", "otheruser");
        MessageThread thread = savedThread("My Thread");
        addParticipant(thread, user);

        MessageThread otherThread = savedThread("Not Mine");
        addParticipant(otherThread, other);

        List<MessageThread> result = threadRepo.findByParticipantUserId(user.getId());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getSubject()).isEqualTo("My Thread");
    }

    @Test
    void findByParticipantUserId_returnsEmpty_whenUserHasNoThreads() {
        User user = savedUser("lonely@test.com", "lonelyuser");

        assertThat(threadRepo.findByParticipantUserId(user.getId())).isEmpty();
    }

    @Test
    void findByParticipantUserId_returnsMultipleThreads_whenUserIsInMultiple() {
        User user = savedUser("multi@test.com", "multiuser");
        MessageThread t1 = savedThread("Thread 1");
        MessageThread t2 = savedThread("Thread 2");
        addParticipant(t1, user);
        addParticipant(t2, user);

        List<MessageThread> result = threadRepo.findByParticipantUserId(user.getId());

        assertThat(result).hasSize(2);
    }

    // ── isParticipant ─────────────────────────────────────────────────────────

    @Test
    void isParticipant_returnsTrue_whenUserIsInThread() {
        User user = savedUser("isp@test.com", "ispuser");
        MessageThread thread = savedThread("Is-Participant Thread");
        addParticipant(thread, user);

        assertThat(threadRepo.isParticipant(thread.getId(), user.getId())).isTrue();
    }

    @Test
    void isParticipant_returnsFalse_whenUserIsNotInThread() {
        User user   = savedUser("nopart@test.com", "nopartuser");
        MessageThread thread = savedThread("Someone Else's Thread");

        assertThat(threadRepo.isParticipant(thread.getId(), user.getId())).isFalse();
    }

    // ── touchUpdatedAt ────────────────────────────────────────────────────────

    @Test
    void touchUpdatedAt_updatesTimestampOnThread() {
        MessageThread thread = savedThread("Touch Thread");
        Instant originalUpdatedAt = thread.getUpdatedAt();

        Instant newTime = Instant.now().plusSeconds(60).truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        threadRepo.touchUpdatedAt(thread.getId(), newTime);
        em.flush();
        em.clear();

        MessageThread refreshed = em.find(MessageThread.class, thread.getId());
        assertThat(refreshed.getUpdatedAt()).isEqualTo(newTime);
    }

    @TestConfiguration
    @EnableJpaAuditing
    static class AuditingConfig {}
}
