package com.reslife.api.domain.resident;

import com.reslife.api.domain.housing.CheckInStatus;
import com.reslife.api.domain.housing.BookingPolicyCheckRequest;
import com.reslife.api.domain.housing.BookingPolicyCheckResponse;
import com.reslife.api.domain.housing.BookingPolicyEnforcementService;
import com.reslife.api.domain.housing.ResidentBookingRequest;
import com.reslife.api.domain.housing.ResidentBookingResponse;
import com.reslife.api.domain.housing.ResidentBookingService;
import com.reslife.api.domain.housing.ResidentBookingStatusUpdateRequest;
import com.reslife.api.domain.housing.MoveInRecordRequest;
import com.reslife.api.domain.housing.MoveInRecordResponse;
import com.reslife.api.encryption.SensitiveAccessLevel;
import com.reslife.api.security.ReslifeUserDetails;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * REST endpoints for resident records.
 *
 * <h3>Access control</h3>
 * <ul>
 *   <li>Listing residents, fetching a single resident, and filter options
 *       require at least a staff/admin role. Students are excluded from the
 *       directory to enforce self-service boundaries — they access their own
 *       record via {@code GET /api/students/me}.</li>
 *   <li>Create, update, and emergency-contact management require staff/admin.</li>
 *   <li>Emergency-contact reads require staff/admin.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/residents")
public class ResidentController {

    private static final String STAFF_ROLES =
            "hasAnyRole('ADMIN','HOUSING_ADMINISTRATOR','DIRECTOR'," +
            "'RESIDENT_DIRECTOR','RESIDENT_ASSISTANT','RESIDENCE_STAFF','STAFF')";

    private final ResidentService residentService;
    private final BookingPolicyEnforcementService bookingPolicyEnforcementService;
    private final ResidentBookingService residentBookingService;

    public ResidentController(ResidentService residentService,
                              BookingPolicyEnforcementService bookingPolicyEnforcementService,
                              ResidentBookingService residentBookingService) {
        this.residentService = residentService;
        this.bookingPolicyEnforcementService = bookingPolicyEnforcementService;
        this.residentBookingService = residentBookingService;
    }

    // ── Directory ─────────────────────────────────────────────────────────────

    @GetMapping
    @PreAuthorize(STAFF_ROLES)
    public Page<ResidentResponse> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String building,
            @RequestParam(required = false) Integer classYear,
            @RequestParam(required = false) CheckInStatus moveInStatus,
            Pageable pageable,
            @AuthenticationPrincipal ReslifeUserDetails principal) {
        SensitiveAccessLevel level = SensitiveAccessLevel.from(principal.getAuthorities());
        return residentService.search(q, building, classYear, moveInStatus, pageable)
                .map(r -> ResidentResponse.from(r, level));
    }

    @GetMapping("/filter-options")
    @PreAuthorize(STAFF_ROLES)
    public FilterOptionsResponse filterOptions() {
        return residentService.filterOptions();
    }

    // ── Duplicate check ───────────────────────────────────────────────────────

    /**
     * Pre-create duplicate check.
     * Returns the list of likely duplicate residents — empty list means no duplicates found.
     *
     * @param excludeId  pass the current resident's ID when editing, so the record
     *                   is not reported as its own duplicate
     */
    @GetMapping("/duplicate-check")
    @PreAuthorize(STAFF_ROLES)
    public DuplicateCheckResponse duplicateCheck(
            @RequestParam(required = false) String firstName,
            @RequestParam(required = false) String lastName,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String studentId,
            @RequestParam(required = false) LocalDate dateOfBirth,
            @RequestParam(required = false) UUID excludeId) {
        return residentService.checkDuplicates(
                firstName, lastName, email, studentId, dateOfBirth, excludeId);
    }

    // ── Single resident ───────────────────────────────────────────────────────

    @GetMapping("/{id}")
    @PreAuthorize(STAFF_ROLES)
    public ResidentResponse get(
            @PathVariable UUID id,
            @AuthenticationPrincipal ReslifeUserDetails principal) {
        SensitiveAccessLevel level = SensitiveAccessLevel.from(principal.getAuthorities());
        return ResidentResponse.from(residentService.findById(id), level);
    }

    // ── Create ────────────────────────────────────────────────────────────────

    /**
     * Creates a resident.
     *
     * <p>Without {@code ?force=true}: returns {@code 409 Conflict} with a
     * {@link DuplicateCheckResponse} body if likely duplicates are found —
     * the client should present the candidates and let the user confirm before
     * retrying with {@code force=true}.
     *
     * <p>With {@code ?force=true}: skips duplicate detection and creates regardless.
     */
    @PostMapping
    @PreAuthorize(STAFF_ROLES)
    public ResponseEntity<ResidentResponse> create(
            @Valid @RequestBody ResidentRequest req,
            @RequestParam(defaultValue = "false") boolean force,
            @AuthenticationPrincipal ReslifeUserDetails principal) {
        SensitiveAccessLevel level = SensitiveAccessLevel.from(principal.getAuthorities());
        ResidentResponse response = ResidentResponse.from(
                residentService.create(req, force), level);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ── Update ────────────────────────────────────────────────────────────────

    @PutMapping("/{id}")
    @PreAuthorize(STAFF_ROLES)
    public ResidentResponse update(
            @PathVariable UUID id,
            @Valid @RequestBody ResidentRequest req,
            @AuthenticationPrincipal ReslifeUserDetails principal) {
        SensitiveAccessLevel level = SensitiveAccessLevel.from(principal.getAuthorities());
        return ResidentResponse.from(residentService.update(id, req), level);
    }

    // ── Soft delete ───────────────────────────────────────────────────────────

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','HOUSING_ADMINISTRATOR','DIRECTOR')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        residentService.softDelete(id);
    }

    // ── Emergency contacts ────────────────────────────────────────────────────

    @GetMapping("/{id}/emergency-contacts")
    @PreAuthorize(STAFF_ROLES)
    public List<EmergencyContactResponse> getEmergencyContacts(
            @PathVariable UUID id,
            @AuthenticationPrincipal ReslifeUserDetails principal) {
        SensitiveAccessLevel level = SensitiveAccessLevel.from(principal.getAuthorities());
        return residentService.findEmergencyContacts(id)
                .stream()
                .map(c -> EmergencyContactResponse.from(c, level))
                .toList();
    }

    @PostMapping("/{id}/emergency-contacts")
    @PreAuthorize(STAFF_ROLES)
    @ResponseStatus(HttpStatus.CREATED)
    public EmergencyContactResponse addEmergencyContact(
            @PathVariable UUID id,
            @Valid @RequestBody EmergencyContactRequest req,
            @AuthenticationPrincipal ReslifeUserDetails principal) {
        SensitiveAccessLevel level = SensitiveAccessLevel.from(principal.getAuthorities());
        return EmergencyContactResponse.from(
                residentService.addEmergencyContact(id, req), level);
    }

    @PutMapping("/{id}/emergency-contacts/{contactId}")
    @PreAuthorize(STAFF_ROLES)
    public EmergencyContactResponse updateEmergencyContact(
            @PathVariable UUID id,
            @PathVariable UUID contactId,
            @Valid @RequestBody EmergencyContactRequest req,
            @AuthenticationPrincipal ReslifeUserDetails principal) {
        SensitiveAccessLevel level = SensitiveAccessLevel.from(principal.getAuthorities());
        return EmergencyContactResponse.from(
                residentService.updateEmergencyContact(id, contactId, req), level);
    }

    @DeleteMapping("/{id}/emergency-contacts/{contactId}")
    @PreAuthorize(STAFF_ROLES)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeEmergencyContact(
            @PathVariable UUID id,
            @PathVariable UUID contactId) {
        residentService.removeEmergencyContact(id, contactId);
    }

    // ── Move-in records ───────────────────────────────────────────────────────

    @GetMapping("/{id}/move-in-records")
    @PreAuthorize(STAFF_ROLES)
    public List<MoveInRecordResponse> getMoveInRecords(@PathVariable UUID id) {
        return residentService.findMoveInRecords(id)
                .stream()
                .map(MoveInRecordResponse::from)
                .toList();
    }

    @PostMapping("/{id}/move-in-records")
    @PreAuthorize(STAFF_ROLES)
    @ResponseStatus(HttpStatus.CREATED)
    public MoveInRecordResponse addMoveInRecord(
            @PathVariable UUID id,
            @Valid @RequestBody MoveInRecordRequest req) {
        return MoveInRecordResponse.from(residentService.addMoveInRecord(id, req));
    }

    @PutMapping("/{id}/move-in-records/{recordId}")
    @PreAuthorize(STAFF_ROLES)
    public MoveInRecordResponse updateMoveInRecord(
            @PathVariable UUID id,
            @PathVariable UUID recordId,
            @Valid @RequestBody MoveInRecordRequest req) {
        return MoveInRecordResponse.from(residentService.updateMoveInRecord(id, recordId, req));
    }

    // ── Bookings ───────────────────────────────────────────────────────────────

    @GetMapping("/{id}/bookings")
    @PreAuthorize(STAFF_ROLES)
    public List<ResidentBookingResponse> getBookings(@PathVariable UUID id) {
        return residentBookingService.findByResident(id).stream()
                .map(ResidentBookingResponse::from)
                .toList();
    }

    @PostMapping("/{id}/bookings")
    @PreAuthorize(STAFF_ROLES)
    @ResponseStatus(HttpStatus.CREATED)
    public ResidentBookingResponse createBooking(
            @PathVariable UUID id,
            @Valid @RequestBody ResidentBookingRequest request,
            @AuthenticationPrincipal ReslifeUserDetails principal) {
        return ResidentBookingResponse.from(
                residentBookingService.createBooking(id, request, principal.getUserId()));
    }

    @PatchMapping("/{id}/bookings/{bookingId}/status")
    @PreAuthorize(STAFF_ROLES)
    public ResidentBookingResponse updateBookingStatus(
            @PathVariable UUID id,
            @PathVariable UUID bookingId,
            @Valid @RequestBody ResidentBookingStatusUpdateRequest request,
            @AuthenticationPrincipal ReslifeUserDetails principal) {
        return ResidentBookingResponse.from(
                residentBookingService.updateStatus(
                        id, bookingId, request.status(), request.reason(), principal.getUserId()));
    }

    // ── Booking policy enforcement preview ─────────────────────────────────────

    /**
     * Evaluates a proposed booking date/building against the currently active
     * booking policy and the resident's recent no-show history.
     */
    @PostMapping("/{id}/booking-policy-check")
    @PreAuthorize(STAFF_ROLES)
    public BookingPolicyCheckResponse checkBookingPolicy(
            @PathVariable UUID id,
            @Valid @RequestBody BookingPolicyCheckRequest req) {
        return bookingPolicyEnforcementService.evaluate(id, req);
    }
}
