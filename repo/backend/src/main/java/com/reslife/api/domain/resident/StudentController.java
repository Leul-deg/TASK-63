package com.reslife.api.domain.resident;

import com.reslife.api.domain.housing.ResidentBookingResponse;
import com.reslife.api.domain.housing.ResidentBookingService;
import com.reslife.api.encryption.SensitiveAccessLevel;
import com.reslife.api.security.ReslifeUserDetails;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/students")
public class StudentController {

    private final ResidentService residentService;
    private final ResidentBookingService residentBookingService;

    public StudentController(ResidentService residentService,
                             ResidentBookingService residentBookingService) {
        this.residentService = residentService;
        this.residentBookingService = residentBookingService;
    }

    /**
     * Student self-view: returns the Resident record explicitly linked to the
     * authenticated student account.
     *
     * <p>Access is restricted to the STUDENT role. Staff and admin must use
     * {@code GET /api/residents/{id}} instead.
     *
     * <p>Returns HTTP 404 if no resident record is linked to the account yet.
     */
    @GetMapping("/me")
    @PreAuthorize("hasRole('STUDENT')")
    public ResidentResponse getSelf(@AuthenticationPrincipal ReslifeUserDetails principal) {
        Resident resident = residentService.findByLinkedUserId(principal.getUserId());
        return ResidentResponse.from(resident, SensitiveAccessLevel.NONE);
    }

    @GetMapping("/me/bookings")
    @PreAuthorize("hasRole('STUDENT')")
    public List<ResidentBookingResponse> getMyBookings(
            @AuthenticationPrincipal ReslifeUserDetails principal) {
        Resident resident = residentService.findByLinkedUserId(principal.getUserId());
        return residentBookingService.findByResident(resident.getId()).stream()
                .map(ResidentBookingResponse::from)
                .toList();
    }
}
