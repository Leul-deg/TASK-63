package com.reslife.api.auth;

import com.reslife.api.domain.system.AuditLogRepository;
import com.reslife.api.domain.user.*;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AuthServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final FailedLoginAttemptRepository attemptRepository = mock(FailedLoginAttemptRepository.class);
    private final AuditLogRepository auditLogRepository = mock(AuditLogRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                userRepository, attemptRepository, auditLogRepository, passwordEncoder);
    }

    @Test
    void login_succeedsForActiveUserWithCorrectPassword() {
        User user = buildUser(AccountStatus.ACTIVE);
        when(userRepository.findByUsernameIgnoreCase("student")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password", "stored-hash")).thenReturn(true);
        when(attemptRepository.countRecentFailures(eq(user), any(Instant.class))).thenReturn(0L);

        var details = authService.login(new LoginRequest("student", "password"), mockRequest());

        assertEquals(user.getId(), details.getUserId());
        verify(attemptRepository).save(argThat(FailedLoginAttempt::isSucceeded));
    }

    @Test
    void login_blocksAfterFiveRecentFailures() {
        User user = buildUser(AccountStatus.ACTIVE);
        when(userRepository.findByUsernameIgnoreCase("student")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password", "stored-hash")).thenReturn(true);
        when(attemptRepository.countRecentFailures(eq(user), any(Instant.class))).thenReturn(5L);

        assertThrows(BadCredentialsException.class,
                () -> authService.login(new LoginRequest("student", "password"), mockRequest()));

        verify(auditLogRepository).save(argThat(a -> "LOGIN_BLOCKED_LOCKOUT".equals(a.getAction())));
    }

    @Test
    void login_rejectsDisabledAccountEvenWithCorrectPassword() {
        User user = buildUser(AccountStatus.DISABLED);
        when(userRepository.findByUsernameIgnoreCase("student")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password", "stored-hash")).thenReturn(true);
        when(attemptRepository.countRecentFailures(eq(user), any(Instant.class))).thenReturn(0L);

        assertThrows(BadCredentialsException.class,
                () -> authService.login(new LoginRequest("student", "password"), mockRequest()));

        verify(auditLogRepository).save(argThat(a -> "LOGIN_BLOCKED_STATUS".equals(a.getAction())));
    }

    @Test
    void login_rejectsFrozenAccountEvenWithCorrectPassword() {
        User user = buildUser(AccountStatus.FROZEN);
        when(userRepository.findByUsernameIgnoreCase("student")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password", "stored-hash")).thenReturn(true);
        when(attemptRepository.countRecentFailures(eq(user), any(Instant.class))).thenReturn(0L);

        assertThrows(BadCredentialsException.class,
                () -> authService.login(new LoginRequest("student", "password"), mockRequest()));
    }

    private static User buildUser(AccountStatus status) {
        Role role = mock(Role.class);
        when(role.getName()).thenReturn(RoleName.STUDENT);
        UserRole userRole = mock(UserRole.class);
        when(userRole.getRole()).thenReturn(role);

        User user = mock(User.class);
        when(user.getId()).thenReturn(UUID.randomUUID());
        when(user.getUsername()).thenReturn("student");
        when(user.getPasswordHash()).thenReturn("stored-hash");
        when(user.getAccountStatus()).thenReturn(status);
        when(user.getUserRoles()).thenReturn(Set.of(userRole));
        return user;
    }

    private static HttpServletRequest mockRequest() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(request.getHeader("User-Agent")).thenReturn("junit");
        return request;
    }
}
