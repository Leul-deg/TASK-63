package com.reslife.api.admin;

import com.reslife.api.domain.system.AuditLog;
import com.reslife.api.domain.system.AuditLogRepository;
import com.reslife.api.domain.user.AccountStatus;
import com.reslife.api.domain.user.FailedLoginAttemptRepository;
import com.reslife.api.domain.user.User;
import com.reslife.api.domain.user.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Verifies the audit-log preservation guarantees introduced by Blocker #3:
 *
 * <ul>
 *   <li>A purged user no longer exists (hard-delete path is exercised).</li>
 *   <li>Audit rows survive — the service writes them before the FK can be nulled.</li>
 *   <li>Audit rows retain meaningful actor metadata via snapshot columns
 *       (actor_username / actor_email), so they remain readable after the actor's
 *       user row is hard-purged and the FK becomes NULL.</li>
 * </ul>
 */
class AuditLogActorPreservationTest {

    private final UserRepository         userRepository    = mock(UserRepository.class);
    private final AuditLogRepository     auditLogRepository = mock(AuditLogRepository.class);
    private final FailedLoginAttemptRepository attemptRepository = mock(FailedLoginAttemptRepository.class);
    @SuppressWarnings("unchecked")
    private final FindByIndexNameSessionRepository<Session> sessionRepository =
            mock(FindByIndexNameSessionRepository.class);

    private AdminUserService service;

    private final UUID targetId = UUID.randomUUID();
    private final UUID actorId  = UUID.randomUUID();

    private static final String ACTOR_USERNAME = "admin.smith";
    private static final String ACTOR_EMAIL    = "admin.smith@reslife.local";

    @BeforeEach
    void setUp() {
        service = new AdminUserService(
                userRepository, auditLogRepository, attemptRepository, sessionRepository);

        when(sessionRepository.findByPrincipalName(any())).thenReturn(Map.of());
    }

    // ── A purged user no longer exists ────────────────────────────────────────

    /**
     * purgeExpiredAccounts() must invoke hardDeletePurgeableUsers(), which issues
     * a native DELETE removing eligible rows from the users table.
     * This proves the "purged user no longer exists" guarantee at the service level.
     */
    @Test
    void purgeExpiredAccounts_hardDeletesEligibleUsers() {
        when(userRepository.hardDeletePurgeableUsers(any(Instant.class))).thenReturn(1);

        service.purgeExpiredAccounts();

        verify(userRepository).hardDeletePurgeableUsers(any(Instant.class));
    }

    // ── Audit rows still exist ────────────────────────────────────────────────

    /**
     * deleteAccount() must persist the audit log entry before the transaction commits.
     * Because the FK is ON DELETE SET NULL (migration V15), the row survives the
     * subsequent hard purge with user_id nulled — it is never deleted itself.
     */
    @Test
    void deleteAccount_auditRowIsSavedAndSurvivesPurge() {
        stubTargetAndActor();

        service.deleteAccount(targetId, actorId, mockRequest());

        // Audit row is written — the ON DELETE SET NULL FK means it persists after purge
        verify(auditLogRepository).save(any(AuditLog.class));
    }

    // ── Audit rows keep meaningful actor metadata ─────────────────────────────

    /**
     * The audit entry must capture actor_username and actor_email at write time.
     * After a hard purge sets user_id to NULL, these columns are the only remaining
     * record of who performed the action.
     */
    @Test
    void deleteAccount_auditRowPreservesActorUsername() {
        stubTargetAndActor();

        service.deleteAccount(targetId, actorId, mockRequest());

        AuditLog saved = captureAuditLog();
        assertEquals(ACTOR_USERNAME, saved.getActorUsername(),
                "actor_username snapshot must be populated so it survives user purge");
    }

    @Test
    void deleteAccount_auditRowPreservesActorEmail() {
        stubTargetAndActor();

        service.deleteAccount(targetId, actorId, mockRequest());

        AuditLog saved = captureAuditLog();
        assertEquals(ACTOR_EMAIL, saved.getActorEmail(),
                "actor_email snapshot must be populated so it survives user purge");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void stubTargetAndActor() {
        User target = new User();
        target.setUsername("student.jones");
        target.setEmail("student.jones@reslife.local");
        target.setPasswordHash("hash");
        target.setAccountStatus(AccountStatus.ACTIVE);
        when(userRepository.findById(targetId)).thenReturn(Optional.of(target));

        User actor = new User();
        actor.setUsername(ACTOR_USERNAME);
        actor.setEmail(ACTOR_EMAIL);
        actor.setPasswordHash("hash");
        actor.setAccountStatus(AccountStatus.ACTIVE);
        when(userRepository.findById(actorId)).thenReturn(Optional.of(actor));
    }

    private AuditLog captureAuditLog() {
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        return captor.getValue();
    }

    private static HttpServletRequest mockRequest() {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getHeader("X-Forwarded-For")).thenReturn(null);
        when(req.getRemoteAddr()).thenReturn("127.0.0.1");
        when(req.getHeader("User-Agent")).thenReturn("test-agent");
        return req;
    }
}
