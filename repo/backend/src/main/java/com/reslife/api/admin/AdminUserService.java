package com.reslife.api.admin;

import com.reslife.api.domain.system.AuditLog;
import com.reslife.api.domain.system.AuditLogRepository;
import com.reslife.api.domain.user.AccountStatus;
import com.reslife.api.domain.user.FailedLoginAttemptRepository;
import com.reslife.api.domain.user.User;
import com.reslife.api.domain.user.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.PersistenceContext;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class AdminUserService {

    @PersistenceContext
    private EntityManager em;

    private final UserRepository userRepository;
    private final AuditLogRepository auditLogRepository;
    private final FailedLoginAttemptRepository attemptRepository;
    private final FindByIndexNameSessionRepository<? extends Session> sessionRepository;

    public AdminUserService(UserRepository userRepository,
                            AuditLogRepository auditLogRepository,
                            FailedLoginAttemptRepository attemptRepository,
                            FindByIndexNameSessionRepository<? extends Session> sessionRepository) {
        this.userRepository = userRepository;
        this.auditLogRepository = auditLogRepository;
        this.attemptRepository = attemptRepository;
        this.sessionRepository = sessionRepository;
    }

    // -----------------------------------------------------------------------
    // User listing / search
    // -----------------------------------------------------------------------

    /**
     * Paginated, searchable list of users for the admin governance screen.
     *
     * <p>Uses a native query so that soft-deleted accounts (those with
     * {@code deleted_at IS NOT NULL}) can be optionally included; the
     * {@code @SQLRestriction} on {@link User} would otherwise exclude them
     * from every JPQL query.
     *
     * @param rawQ          free-text search across username, email, first/last name
     * @param statusFilter  optional exact-match filter on {@code account_status}
     * @param includeDeleted when {@code true}, soft-deleted rows are included
     * @param page          zero-based page index
     * @param size          page size (callers should cap this, e.g. at 100)
     */
    public Page<UserSummaryDto> listUsers(String rawQ, AccountStatus statusFilter,
                                          boolean includeDeleted, int page, int size) {
        String q = rawQ == null ? "" : rawQ.strip();
        boolean hasQ = !q.isEmpty();
        String likeParam = "%" + q.toLowerCase() + "%";

        // Build WHERE clause dynamically to avoid null-parameter binding issues
        // in native queries (some JDBC drivers reject typed-null params).
        StringBuilder where = new StringBuilder("1=1");
        if (hasQ) {
            where.append(" AND (LOWER(username) LIKE :like OR LOWER(email) LIKE :like" +
                         " OR LOWER(first_name) LIKE :like OR LOWER(last_name) LIKE :like)");
        }
        if (statusFilter != null) {
            where.append(" AND account_status = :status");
        }
        if (!includeDeleted) {
            where.append(" AND deleted_at IS NULL");
        }
        String w = where.toString();

        // Count
        jakarta.persistence.Query countQ =
                em.createNativeQuery("SELECT COUNT(*) FROM users WHERE " + w);
        if (hasQ)             countQ.setParameter("like", likeParam);
        if (statusFilter != null) countQ.setParameter("status", statusFilter.name());
        long total = ((Number) countQ.getSingleResult()).longValue();

        // Data — native query bypasses @SQLRestriction, allowing soft-deleted rows
        jakarta.persistence.Query dataQ = em.createNativeQuery(
                "SELECT * FROM users WHERE " + w + " ORDER BY created_at DESC", User.class);
        if (hasQ)             dataQ.setParameter("like", likeParam);
        if (statusFilter != null) dataQ.setParameter("status", statusFilter.name());
        dataQ.setFirstResult(page * size);
        dataQ.setMaxResults(size);

        @SuppressWarnings("unchecked")
        List<User> results = (List<User>) dataQ.getResultList();

        List<UserSummaryDto> content = results.stream().map(UserSummaryDto::from).toList();
        return new PageImpl<>(content, PageRequest.of(page, size), total);
    }

    // -----------------------------------------------------------------------
    // Account status management
    // -----------------------------------------------------------------------

    /**
     * Changes a user's account status.
     *
     * <p>Rules:
     * <ul>
     *   <li>An admin cannot change their own account status.</li>
     *   <li>Disabling/freeze/blacklist affect sign-in only; they do not schedule purge.</li>
     *   <li>Only the explicit delete workflow starts the 30-day purge clock.</li>
     *   <li>Any status change immediately kills all active sessions for the target user.</li>
     * </ul>
     */
    @Transactional
    public User updateAccountStatus(UUID targetUserId, AccountStatus newStatus, String reason,
                                    UUID actorUserId, HttpServletRequest httpRequest) {
        if (targetUserId.equals(actorUserId)) {
            throw new IllegalArgumentException("Administrators cannot change their own account status.");
        }

        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + targetUserId));
        if (target.isDeleted()) {
            throw new IllegalArgumentException(
                    "Soft-deleted accounts cannot change status; they must be purged or restored through a dedicated workflow.");
        }

        AccountStatus oldStatus = target.getAccountStatus();

        target.setAccountStatus(newStatus);
        target.setStatusReason(reason);
        target.setStatusChangedBy(actorUserId);
        target.setStatusChangedAt(Instant.now());

        // Status changes do not schedule deletion. The 30-day purge window is
        // started only by deleteAccount(), which also sets deleted_at.
        if (newStatus == AccountStatus.ACTIVE && !target.isDeleted()) {
            target.setScheduledPurgeAt(null);
        }

        userRepository.save(target);

        // Immediately expire all active sessions for the affected user
        invalidateUserSessions(target.getUsername());

        // Audit the action
        String ip = extractClientIp(httpRequest);
        String ua = httpRequest.getHeader("User-Agent");
        auditStatusChange(target, oldStatus, newStatus, reason, actorUserId, ip, ua);

        return target;
    }

    // -----------------------------------------------------------------------
    // Account deletion
    // -----------------------------------------------------------------------

    /**
     * Soft-deletes a user account and schedules it for permanent purge in 30 days.
     *
     * <p>Unlike {@link #updateAccountStatus}, this is a one-way operation:
     * the account is immediately soft-deleted ({@code deleted_at} set) so the
     * nightly purge job can find and permanently remove it when the grace period
     * expires. All active sessions are invalidated and the action is audit-logged.
     */
    @Transactional
    public void deleteAccount(UUID targetUserId, UUID actorUserId, HttpServletRequest httpRequest) {
        if (targetUserId.equals(actorUserId)) {
            throw new IllegalArgumentException("Administrators cannot delete their own account.");
        }

        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + targetUserId));

        target.softDelete();
        target.setScheduledPurgeAt(Instant.now().plus(30, ChronoUnit.DAYS));
        target.setAccountStatus(AccountStatus.DISABLED);
        target.setStatusChangedBy(actorUserId);
        target.setStatusChangedAt(Instant.now());

        userRepository.save(target);

        invalidateUserSessions(target.getUsername());

        User actor = userRepository.findById(actorUserId).orElse(null);
        String ip = extractClientIp(httpRequest);
        String ua = httpRequest.getHeader("User-Agent");

        AuditLog entry = new AuditLog();
        entry.setUser(actor);
        snapshotActor(entry, actor);
        entry.setAction("ACCOUNT_DELETED");
        entry.setEntityType("User");
        entry.setEntityId(target.getId());
        entry.setNewValues("{\"scheduledPurgeAt\":\"" + target.getScheduledPurgeAt() + "\"}");
        entry.setIpAddress(ip);
        entry.setUserAgent(ua);
        auditLogRepository.save(entry);
    }

    // -----------------------------------------------------------------------
    // Scheduled maintenance
    // -----------------------------------------------------------------------

    /**
     * Hard-deletes accounts whose 30-day grace period has expired.
     * Runs daily at 02:00. A deletion is logged as an audit entry before
     * the row is removed.
     */
    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void purgeExpiredAccounts() {
        Instant now = Instant.now();
        int purged = userRepository.hardDeletePurgeableUsers(now);
        if (purged > 0) {
            AuditLog entry = new AuditLog();
            entry.setAction("PURGE_SCHEDULED");
            entry.setEntityType("User");
            entry.setNewValues("{\"purgedCount\":" + purged + "}");
            auditLogRepository.save(entry);
        }
    }

    /**
     * Removes login attempt records older than 30 days to control table growth.
     * Runs daily at 03:00.
     */
    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void cleanupLoginAttempts() {
        Instant cutoff = Instant.now().minus(30, ChronoUnit.DAYS);
        attemptRepository.deleteOlderThan(cutoff);
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private void invalidateUserSessions(String username) {
        try {
            Map<String, ? extends Session> sessions =
                    sessionRepository.findByPrincipalName(username);
            sessions.forEach((id, session) -> sessionRepository.deleteById(id));
        } catch (Exception ignored) {
            // Session store may be unavailable during tests; swallow silently
        }
    }

    private void auditStatusChange(User target, AccountStatus oldStatus, AccountStatus newStatus,
                                   String reason, UUID actorUserId, String ip, String ua) {
        User actor = userRepository.findById(actorUserId).orElse(null);

        AuditLog entry = new AuditLog();
        entry.setUser(actor);
        snapshotActor(entry, actor);
        entry.setAction("ACCOUNT_STATUS_CHANGE");
        entry.setEntityType("User");
        entry.setEntityId(target.getId());
        entry.setOldValues("{\"accountStatus\":\"" + oldStatus + "\"}");
        entry.setNewValues("{\"accountStatus\":\"" + newStatus + "\",\"reason\":\""
                + (reason != null ? reason.replace("\"", "\\\"") : "") + "\"}");
        entry.setIpAddress(ip);
        entry.setUserAgent(ua);
        auditLogRepository.save(entry);
    }

    /**
     * Copies the actor's username and email into the audit entry at write time.
     * These snapshot columns survive even after the actor's user row is hard-purged
     * (the {@code user_id} FK becomes NULL; the text values remain).
     */
    private void snapshotActor(AuditLog entry, User actor) {
        if (actor != null) {
            entry.setActorUsername(actor.getUsername());
            entry.setActorEmail(actor.getEmail());
        }
    }

    private String extractClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].strip();
        }
        return request.getRemoteAddr();
    }
}
