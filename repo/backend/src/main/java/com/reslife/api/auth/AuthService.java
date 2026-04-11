package com.reslife.api.auth;

import com.reslife.api.domain.user.*;
import com.reslife.api.domain.system.AuditLog;
import com.reslife.api.domain.system.AuditLogRepository;
import com.reslife.api.security.ReslifeUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

@Service
public class AuthService {

    /**
     * Pre-computed BCrypt hash (cost 12) used as a dummy target when the supplied
     * username does not match any account. Running BCrypt.matches() against this
     * dummy ensures response time is indistinguishable from a real failed login,
     * preventing username-enumeration via timing.
     */
    private static final String DUMMY_HASH =
            "$2a$12$GbTHW1fKXOeKNIAH.vQBuOpBPjK0KmFNpqz.qRDT/2QFbmVsGHlsW";

    /** Rolling lockout window: 15 minutes. */
    private static final int LOCKOUT_WINDOW_MINUTES = 15;
    /** Max failures before lockout. */
    private static final int MAX_FAILURES = 5;

    private final UserRepository userRepository;
    private final FailedLoginAttemptRepository attemptRepository;
    private final AuditLogRepository auditLogRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserRepository userRepository,
                       FailedLoginAttemptRepository attemptRepository,
                       AuditLogRepository auditLogRepository,
                       PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.attemptRepository = attemptRepository;
        this.auditLogRepository = auditLogRepository;
        this.passwordEncoder = passwordEncoder;
    }

    // -----------------------------------------------------------------------
    // Login
    // -----------------------------------------------------------------------

    /**
     * Validates credentials and returns a {@link ReslifeUserDetails} on success.
     *
     * <p><b>Timing-safety:</b> BCrypt.matches() always runs regardless of whether
     * the user exists. This makes all failure paths take the same ~300ms, preventing
     * an attacker from enumerating valid usernames by measuring response times.
     *
     * <p><b>Error messages:</b> Every rejection path throws {@link BadCredentialsException}
     * with the same generic message. The caller never learns whether a username exists,
     * whether it is locked, or whether the password was almost correct.
     */
    @Transactional
    public ReslifeUserDetails login(LoginRequest request, HttpServletRequest httpRequest) {
        String identifier = request.identifier().strip();
        String ip = extractClientIp(httpRequest);
        String ua = httpRequest.getHeader("User-Agent");

        // 1. Resolve user — may be absent
        Optional<User> userOpt = userRepository.findByUsernameIgnoreCase(identifier)
                .or(() -> userRepository.findByEmailIgnoreCase(identifier));

        // 2. BCrypt check — ALWAYS runs to ensure uniform response time
        String hashToCheck = userOpt.map(User::getPasswordHash).orElse(DUMMY_HASH);
        boolean passwordOk = passwordEncoder.matches(request.password(), hashToCheck);

        // 3. If we found a user, enforce rolling-window lockout BEFORE revealing any
        //    status information. We reject with the same "Invalid credentials" message
        //    so that lockout does not confirm username existence.
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            Instant windowStart = Instant.now().minus(LOCKOUT_WINDOW_MINUTES, ChronoUnit.MINUTES);
            long recentFailures = attemptRepository.countRecentFailures(user, windowStart);
            if (recentFailures >= MAX_FAILURES) {
                recordAttempt(user, identifier, ip, ua, false);
                audit("LOGIN_BLOCKED_LOCKOUT", user, ip, ua);
                throw new BadCredentialsException("Invalid credentials");
            }
        }

        // 4. Reject wrong password or unknown user — same branch, same message
        if (!passwordOk) {
            userOpt.ifPresent(u -> {
                recordAttempt(u, identifier, ip, ua, false);
                audit("LOGIN_FAILED", u, ip, ua);
            });
            if (userOpt.isEmpty()) {
                recordAttempt(null, identifier, ip, ua, false);
            }
            throw new BadCredentialsException("Invalid credentials");
        }

        // Password was correct — user must be present
        User user = userOpt.get();

        // 5. Check account status AFTER the password check (timing-safe ordering)
        if (user.getAccountStatus() != AccountStatus.ACTIVE) {
            recordAttempt(user, identifier, ip, ua, false);
            audit("LOGIN_BLOCKED_STATUS", user, ip, ua);
            throw new BadCredentialsException("Invalid credentials");
        }

        // 6. Success
        recordAttempt(user, identifier, ip, ua, true);
        audit("LOGIN_SUCCESS", user, ip, ua);

        return ReslifeUserDetails.from(user);
    }

    // -----------------------------------------------------------------------
    // Logout audit
    // -----------------------------------------------------------------------

    @Transactional
    public void recordLogout(ReslifeUserDetails principal, HttpServletRequest httpRequest) {
        userRepository.findById(principal.getUserId()).ifPresent(user ->
                audit("LOGOUT", user, extractClientIp(httpRequest),
                        httpRequest.getHeader("User-Agent")));
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private void recordAttempt(User user, String identifier, String ip, String ua, boolean succeeded) {
        FailedLoginAttempt attempt = new FailedLoginAttempt();
        attempt.setUser(user);
        attempt.setIdentifier(identifier);
        attempt.setIpAddress(ip);
        attempt.setUserAgent(ua);
        attempt.setSucceeded(succeeded);
        attemptRepository.save(attempt);
    }

    private void audit(String action, User user, String ip, String ua) {
        AuditLog entry = new AuditLog();
        entry.setAction(action);
        entry.setEntityType("User");
        entry.setEntityId(user.getId());
        entry.setUser(user);
        entry.setIpAddress(ip);
        entry.setUserAgent(ua);
        auditLogRepository.save(entry);
    }

    private String extractClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].strip();
        }
        return request.getRemoteAddr();
    }
}
