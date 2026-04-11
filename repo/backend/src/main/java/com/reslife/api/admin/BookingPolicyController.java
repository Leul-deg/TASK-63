package com.reslife.api.admin;

import com.reslife.api.security.ReslifeUserDetails;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Admin-only REST endpoints for managing the booking/visit policy.
 *
 * <p>All routes are under {@code /api/admin/} which is restricted to
 * {@code ROLE_ADMIN} by {@code SecurityConfig}.
 *
 * <pre>
 * GET  /api/admin/booking-policy              — current effective policy + version metadata
 * PUT  /api/admin/booking-policy              — create a new policy version
 * GET  /api/admin/booking-policy/history      — all versions, newest first
 * POST /api/admin/booking-policy/activate/{v} — activate a historical version
 * </pre>
 */
@RestController
@RequestMapping("/api/admin/booking-policy")
public class BookingPolicyController {

    private final BookingPolicyService bookingPolicyService;

    public BookingPolicyController(BookingPolicyService bookingPolicyService) {
        this.bookingPolicyService = bookingPolicyService;
    }

    // ── Current effective policy ───────────────────────────────────────────────

    @GetMapping
    public PolicyVersionResponse getCurrent() {
        return bookingPolicyService.getCurrent();
    }

    // ── Update (create new version) ────────────────────────────────────────────

    @PutMapping
    @ResponseStatus(HttpStatus.OK)
    public PolicyVersionResponse update(
            @Valid @RequestBody UpdateBookingPolicyRequest req,
            @AuthenticationPrincipal ReslifeUserDetails principal) {
        return bookingPolicyService.update(
                req.policy(),
                req.description(),
                principal.getUserId());
    }

    // ── Full version history ───────────────────────────────────────────────────

    @GetMapping("/history")
    public List<PolicyVersionResponse> history() {
        return bookingPolicyService.getHistory();
    }

    // ── Activate a historical version ─────────────────────────────────────────

    /**
     * Restores a historical version by creating a new version with the same
     * policy values.  History is never modified.
     *
     * @param version the version number to restore
     */
    @PostMapping("/activate/{version}")
    public PolicyVersionResponse activate(
            @PathVariable int version,
            @AuthenticationPrincipal ReslifeUserDetails principal) {
        return bookingPolicyService.activateVersion(version, principal.getUserId());
    }
}
