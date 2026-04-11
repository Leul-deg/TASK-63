package com.reslife.api.domain.housing;

import com.reslife.api.domain.resident.Resident;
import com.reslife.api.domain.resident.ResidentService;
import com.reslife.api.domain.user.User;
import com.reslife.api.domain.user.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class ResidentBookingService {

    private static final EnumSet<ResidentBookingStatus> ACTIVE_BOOKING_STATUSES =
            EnumSet.of(ResidentBookingStatus.REQUESTED, ResidentBookingStatus.CONFIRMED);

    private final ResidentBookingRepository bookingRepository;
    private final ResidentService residentService;
    private final BookingPolicyEnforcementService bookingPolicyEnforcementService;
    private final UserRepository userRepository;

    public ResidentBookingService(ResidentBookingRepository bookingRepository,
                                  ResidentService residentService,
                                  BookingPolicyEnforcementService bookingPolicyEnforcementService,
                                  UserRepository userRepository) {
        this.bookingRepository = bookingRepository;
        this.residentService = residentService;
        this.bookingPolicyEnforcementService = bookingPolicyEnforcementService;
        this.userRepository = userRepository;
    }

    public List<ResidentBooking> findByResident(UUID residentId) {
        residentService.findById(residentId);
        return bookingRepository.findByResidentIdOrderByRequestedDateDescCreatedAtDesc(residentId);
    }

    @Transactional
    public ResidentBooking createBooking(UUID residentId, ResidentBookingRequest request, UUID actorUserId) {
        Resident resident = residentService.findById(residentId);

        BookingPolicyCheckResponse policy = bookingPolicyEnforcementService.evaluate(
                residentId,
                new BookingPolicyCheckRequest(request.requestedDate(), request.buildingName()));
        if (!policy.allowed()) {
            throw new IllegalArgumentException(String.join(" ", policy.reasons()));
        }

        if (bookingRepository.existsByResidentIdAndRequestedDateAndBuildingNameIgnoreCaseAndStatusIn(
                residentId, request.requestedDate(), request.buildingName(), ACTIVE_BOOKING_STATUSES)) {
            throw new IllegalArgumentException(
                    "An active booking already exists for this resident on the requested date and building.");
        }

        ResidentBooking booking = new ResidentBooking();
        booking.setResident(resident);
        booking.setRequestedDate(request.requestedDate());
        booking.setBuildingName(request.buildingName().trim());
        booking.setRoomNumber(blankToNull(request.roomNumber()));
        booking.setPurpose(blankToNull(request.purpose()));
        booking.setNotes(blankToNull(request.notes()));
        booking.setStatus(ResidentBookingStatus.REQUESTED);
        if (actorUserId != null) {
            User actor = requireUser(actorUserId);
            booking.setCreatedBy(actor);
            booking.setUpdatedBy(actor);
        }
        return bookingRepository.save(booking);
    }

    @Transactional
    public ResidentBooking updateStatus(UUID residentId, UUID bookingId,
                                        ResidentBookingStatus status, String reason,
                                        UUID actorUserId) {
        ResidentBooking booking = requireBookingForResident(residentId, bookingId);
        booking.setStatus(status);
        booking.setDecisionReason(blankToNull(reason));
        if (actorUserId != null) {
            booking.setUpdatedBy(requireUser(actorUserId));
        }
        return bookingRepository.save(booking);
    }

    private ResidentBooking requireBookingForResident(UUID residentId, UUID bookingId) {
        ResidentBooking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new EntityNotFoundException("Booking not found: " + bookingId));
        if (!booking.getResident().getId().equals(residentId)) {
            throw new EntityNotFoundException("Booking not found for resident " + residentId);
        }
        return booking;
    }

    private User requireUser(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + id));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
