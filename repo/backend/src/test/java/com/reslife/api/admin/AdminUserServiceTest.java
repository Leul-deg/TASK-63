package com.reslife.api.admin;

import com.reslife.api.domain.system.AuditLogRepository;
import com.reslife.api.domain.user.*;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AdminUserServiceTest {

    @SuppressWarnings("unchecked")
    private final FindByIndexNameSessionRepository<Session> sessionRepo =
            mock(FindByIndexNameSessionRepository.class);
    private final UserRepository               userRepository    = mock(UserRepository.class);
    private final AuditLogRepository           auditLogRepository = mock(AuditLogRepository.class);
    private final FailedLoginAttemptRepository attemptRepository  = mock(FailedLoginAttemptRepository.class);

    private AdminUserService service;

    private static final UUID ACTOR_ID  = UUID.randomUUID();
    private static final UUID TARGET_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new AdminUserService(
                userRepository, auditLogRepository, attemptRepository, sessionRepo);
        when(sessionRepo.findByPrincipalName(any())).thenReturn(Map.of());
        when(auditLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private User activeUser(String username) {
        User u = new User();
        u.setUsername(username);
        u.setEmail(username + "@test.com");
        u.setAccountStatus(AccountStatus.ACTIVE);
        return u;
    }

    // ── updateAccountStatus ───────────────────────────────────────────────────

    @Test
    void updateAccountStatus_throwsWhenActorTargetsSelf() {
        assertThatThrownBy(() ->
                service.updateAccountStatus(ACTOR_ID, AccountStatus.DISABLED, null,
                        ACTOR_ID, new MockHttpServletRequest()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot change their own");
    }

    @Test
    void updateAccountStatus_throwsWhenTargetNotFound() {
        when(userRepository.findById(TARGET_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                service.updateAccountStatus(TARGET_ID, AccountStatus.DISABLED, null,
                        ACTOR_ID, new MockHttpServletRequest()))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining(TARGET_ID.toString());
    }

    @Test
    void updateAccountStatus_throwsWhenTargetIsAlreadySoftDeleted() {
        User deleted = activeUser("deleted-user");
        deleted.softDelete();
        when(userRepository.findById(TARGET_ID)).thenReturn(Optional.of(deleted));

        assertThatThrownBy(() ->
                service.updateAccountStatus(TARGET_ID, AccountStatus.ACTIVE, null,
                        ACTOR_ID, new MockHttpServletRequest()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Soft-deleted");
    }

    @Test
    void updateAccountStatus_updatesStatusAndInvalidatesSessions() {
        User target = activeUser("target-user");
        when(userRepository.findById(TARGET_ID)).thenReturn(Optional.of(target));
        when(userRepository.findById(ACTOR_ID)).thenReturn(Optional.empty());

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("X-Forwarded-For", "192.168.1.1");

        service.updateAccountStatus(
                TARGET_ID, AccountStatus.DISABLED, "Policy violation", ACTOR_ID, req);

        assertThat(target.getAccountStatus()).isEqualTo(AccountStatus.DISABLED);
        assertThat(target.getStatusReason()).isEqualTo("Policy violation");
        assertThat(target.getStatusChangedBy()).isEqualTo(ACTOR_ID);
        verify(sessionRepo).findByPrincipalName("target-user");
        verify(auditLogRepository).save(any());
    }

    @Test
    void updateAccountStatus_clearsScheduledPurge_whenRestoringToActive() {
        User target = activeUser("restore-user");
        when(userRepository.findById(TARGET_ID)).thenReturn(Optional.of(target));
        when(userRepository.findById(ACTOR_ID)).thenReturn(Optional.empty());

        service.updateAccountStatus(
                TARGET_ID, AccountStatus.ACTIVE, null, ACTOR_ID, new MockHttpServletRequest());

        assertThat(target.getScheduledPurgeAt()).isNull();
    }

    @Test
    void updateAccountStatus_extractsIpFromForwardedHeader() {
        User target = activeUser("ip-user");
        when(userRepository.findById(TARGET_ID)).thenReturn(Optional.of(target));
        when(userRepository.findById(ACTOR_ID)).thenReturn(Optional.empty());

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("X-Forwarded-For", "10.0.0.5, 172.16.0.1");

        service.updateAccountStatus(
                TARGET_ID, AccountStatus.DISABLED, null, ACTOR_ID, req);

        verify(auditLogRepository).save(argThat(log ->
                "10.0.0.5".equals(log.getIpAddress())));
    }

    // ── deleteAccount ─────────────────────────────────────────────────────────

    @Test
    void deleteAccount_throwsWhenActorDeletesSelf() {
        assertThatThrownBy(() ->
                service.deleteAccount(ACTOR_ID, ACTOR_ID, new MockHttpServletRequest()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot delete their own");
    }

    @Test
    void deleteAccount_throwsWhenTargetNotFound() {
        when(userRepository.findById(TARGET_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                service.deleteAccount(TARGET_ID, ACTOR_ID, new MockHttpServletRequest()))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining(TARGET_ID.toString());
    }

    @Test
    void deleteAccount_softDeletesAndSchedulesPurge() {
        User target = activeUser("delete-me");
        when(userRepository.findById(TARGET_ID)).thenReturn(Optional.of(target));
        when(userRepository.findById(ACTOR_ID)).thenReturn(Optional.empty());

        service.deleteAccount(TARGET_ID, ACTOR_ID, new MockHttpServletRequest());

        assertThat(target.isDeleted()).isTrue();
        assertThat(target.getAccountStatus()).isEqualTo(AccountStatus.DISABLED);
        assertThat(target.getScheduledPurgeAt()).isAfter(Instant.now().minusSeconds(10));
        verify(userRepository).save(target);
        verify(auditLogRepository).save(any());
        verify(sessionRepo).findByPrincipalName("delete-me");
    }

    // ── purgeExpiredAccounts ──────────────────────────────────────────────────

    @Test
    void purgeExpiredAccounts_logsAuditEntry_whenUsersArePurged() {
        when(userRepository.hardDeletePurgeableUsers(any())).thenReturn(5);

        service.purgeExpiredAccounts();

        verify(auditLogRepository).save(argThat(log ->
                "PURGE_SCHEDULED".equals(log.getAction())));
    }

    @Test
    void purgeExpiredAccounts_doesNotAuditLog_whenNoPurgableUsers() {
        when(userRepository.hardDeletePurgeableUsers(any())).thenReturn(0);

        service.purgeExpiredAccounts();

        verify(auditLogRepository, never()).save(any());
    }

    // ── cleanupLoginAttempts ──────────────────────────────────────────────────

    @Test
    void cleanupLoginAttempts_deletesAttemptsOlderThan30Days() {
        service.cleanupLoginAttempts();

        verify(attemptRepository).deleteOlderThan(argThat(cutoff ->
                cutoff.isBefore(Instant.now()) &&
                cutoff.isAfter(Instant.now().minusSeconds(30 * 24 * 3600 + 60))));
    }
}
