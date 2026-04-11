package com.reslife.api.admin;

import com.reslife.api.auth.LoginResponse;
import com.reslife.api.domain.user.AccountStatus;
import com.reslife.api.domain.user.User;
import com.reslife.api.security.ReslifeUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasAnyRole('ADMIN', 'HOUSING_ADMINISTRATOR')")
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    /**
     * GET /api/admin/users
     *
     * <p>Paginated, searchable list of user accounts for the admin governance screen.
     * Soft-deleted accounts (pending purge) are excluded by default; pass
     * {@code includeDeleted=true} to include them alongside their scheduled purge date.
     *
     * @param q              optional free-text search (username, email, first/last name)
     * @param status         optional exact-match filter on account status
     * @param includeDeleted whether to include soft-deleted accounts (default false)
     * @param page           zero-based page index (default 0)
     * @param size           page size, capped at 100 (default 20)
     */
    @GetMapping
    public ResponseEntity<Page<UserSummaryDto>> listUsers(
            @RequestParam(required = false, defaultValue = "") String q,
            @RequestParam(required = false) AccountStatus status,
            @RequestParam(required = false, defaultValue = "false") boolean includeDeleted,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size) {
        size = Math.min(size, 100);
        Page<UserSummaryDto> result = adminUserService.listUsers(q, status, includeDeleted, page, size);
        return ResponseEntity.ok(result);
    }

    /**
     * DELETE /api/admin/users/{id}
     *
     * <p>Soft-deletes the account and schedules it for permanent purge after a 30-day
     * grace period. All active sessions for the target user are invalidated immediately.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(
            @PathVariable UUID id,
            @AuthenticationPrincipal ReslifeUserDetails actor,
            HttpServletRequest httpRequest) {
        adminUserService.deleteAccount(id, actor.getUserId(), httpRequest);
        return ResponseEntity.noContent().build();
    }

    /**
     * PATCH /api/admin/users/{id}/status
     *
     * <p>Allowed status transitions an admin can apply:
     * <ul>
     *   <li>{@code ACTIVE}      — re-activate a disabled or frozen account</li>
     *   <li>{@code DISABLED}    — disable account sign-in without scheduling deletion</li>
     *   <li>{@code FROZEN}      — temporarily freeze (no purge clock)</li>
     *   <li>{@code BLACKLISTED} — permanent policy violation block</li>
     * </ul>
     *
     * <p>All changes are audit-logged and all active sessions for the target user
     * are invalidated immediately.
     */
    @PatchMapping("/{id}/status")
    public ResponseEntity<LoginResponse> updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateStatusRequest request,
            @AuthenticationPrincipal ReslifeUserDetails actor,
            HttpServletRequest httpRequest) {

        User updated = adminUserService.updateAccountStatus(
                id, request.status(), request.reason(), actor.getUserId(), httpRequest);

        return ResponseEntity.ok(LoginResponse.from(updated));
    }
}
