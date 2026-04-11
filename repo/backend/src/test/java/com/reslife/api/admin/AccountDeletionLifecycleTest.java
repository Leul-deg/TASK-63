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
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Verifies the deletion lifecycle introduced by Blocker #2:
 *
 * <ul>
 *   <li>deleteAccount sets deleted_at on the user row.</li>
 *   <li>deleteAccount sets scheduled_purge_at to 30 days from now.</li>
 *   <li>purgeExpiredAccounts writes an audit entry when rows are hard-deleted.</li>
 *   <li>purgeExpiredAccounts writes no audit entry when no rows are eligible.</li>
 * </ul>
 */
class AccountDeletionLifecycleTest {

    private final UserRepository         userRepository   = mock(UserRepository.class);
    private final AuditLogRepository     auditLogRepository = mock(AuditLogRepository.class);
    private final FailedLoginAttemptRepository attemptRepository = mock(FailedLoginAttemptRepository.class);
    @SuppressWarnings("unchecked")
    private final FindByIndexNameSessionRepository<Session> sessionRepository =
            mock(FindByIndexNameSessionRepository.class);

    private AdminUserService service;

    private final UUID targetId = UUID.randomUUID();
    private final UUID actorId  = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new AdminUserService(
                userRepository, auditLogRepository, attemptRepository, sessionRepository);

        // Session repository: no active sessions for any user
        when(sessionRepository.findByPrincipalName(any())).thenReturn(Map.of());

        // Actor lookup used by audit helper — return empty (actor row may be gone)
        when(userRepository.findById(actorId)).thenReturn(Optional.empty());
    }

    // ── deleteAccount marks the row as soft-deleted ────────────────────────

    @Test
    void deleteAccount_marksAccountAsDeleted() {
        User target = activeUser("alice", "alice@reslife.local");
        when(userRepository.findById(targetId)).thenReturn(Optional.of(target));
        HttpServletRequest req = mockRequest();

        service.deleteAccount(targetId, actorId, req);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertNotNull(saved.getValue().getDeletedAt(),
                "deleted_at must be set so the purge query can find this row");
    }

    // ── deleteAccount sets the purge timestamp 30 days out ─────────────────

    @Test
    void deleteAccount_setsScheduledPurgeDateThirtyDaysOut() {
        User target = activeUser("bob", "bob@reslife.local");
        when(userRepository.findById(targetId)).thenReturn(Optional.of(target));
        HttpServletRequest req = mockRequest();

        Instant before = Instant.now();
        service.deleteAccount(targetId, actorId, req);
        Instant after = Instant.now();

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());

        Instant purgeAt = saved.getValue().getScheduledPurgeAt();
        assertNotNull(purgeAt, "scheduled_purge_at must be set");
        assertTrue(purgeAt.isAfter(before.plus(29, ChronoUnit.DAYS)),
                "scheduled purge should be at least 29 days out");
        assertTrue(purgeAt.isBefore(after.plus(31, ChronoUnit.DAYS)),
                "scheduled purge should be at most 31 days out");
    }

    @Test
    void disablingAccount_doesNotSchedulePurge() {
        User target = activeUser("carol", "carol@reslife.local");
        when(userRepository.findById(targetId)).thenReturn(Optional.of(target));
        HttpServletRequest req = mockRequest();

        service.updateAccountStatus(targetId, AccountStatus.DISABLED, "policy hold", actorId, req);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertNull(saved.getValue().getScheduledPurgeAt(),
                "DISABLED status alone must not schedule hard deletion");
    }

    @Test
    void softDeletedAccount_cannotChangeStatus() {
        User target = activeUser("dave", "dave@reslife.local");
        target.softDelete();
        when(userRepository.findById(targetId)).thenReturn(Optional.of(target));

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> service.updateAccountStatus(targetId, AccountStatus.ACTIVE, "restore", actorId, mockRequest()));

        assertTrue(ex.getMessage().contains("Soft-deleted accounts"));
        verify(userRepository, never()).save(any(User.class));
    }

    // ── purgeExpiredAccounts writes an audit log when rows are deleted ──────

    @Test
    void purgeExpiredAccounts_logsAuditWhenAccountsPurged() {
        when(userRepository.hardDeletePurgeableUsers(any(Instant.class))).thenReturn(3);

        service.purgeExpiredAccounts();

        ArgumentCaptor<AuditLog> logCaptor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(logCaptor.capture());
        assertEquals("PURGE_SCHEDULED", logCaptor.getValue().getAction());
        assertTrue(logCaptor.getValue().getNewValues().contains("3"),
                "audit entry should record the number of purged accounts");
    }

    // ── purgeExpiredAccounts writes no audit when nothing is eligible ───────

    @Test
    void purgeExpiredAccounts_skipsAuditWhenNothingEligible() {
        when(userRepository.hardDeletePurgeableUsers(any(Instant.class))).thenReturn(0);

        service.purgeExpiredAccounts();

        verify(auditLogRepository, never()).save(any());
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private static User activeUser(String username, String email) {
        User u = new User();
        u.setUsername(username);
        u.setEmail(email);
        u.setPasswordHash("hash");
        u.setAccountStatus(AccountStatus.ACTIVE);
        return u;
    }

    private static HttpServletRequest mockRequest() {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getHeader("X-Forwarded-For")).thenReturn(null);
        when(req.getRemoteAddr()).thenReturn("127.0.0.1");
        when(req.getHeader("User-Agent")).thenReturn("test-agent");
        return req;
    }
}
