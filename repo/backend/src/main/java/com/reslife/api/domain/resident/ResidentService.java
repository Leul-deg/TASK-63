package com.reslife.api.domain.resident;

import com.reslife.api.domain.housing.CheckInStatus;
import com.reslife.api.domain.housing.MoveInRecord;
import com.reslife.api.domain.housing.MoveInRecordRepository;
import com.reslife.api.domain.housing.MoveInRecordRequest;
import com.reslife.api.domain.user.RoleName;
import com.reslife.api.domain.user.User;
import com.reslife.api.domain.user.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class ResidentService {

    private final ResidentRepository          residentRepository;
    private final EmergencyContactRepository  emergencyContactRepository;
    private final MoveInRecordRepository      moveInRecordRepository;
    private final UserRepository              userRepository;

    public ResidentService(ResidentRepository residentRepository,
                           EmergencyContactRepository emergencyContactRepository,
                           MoveInRecordRepository moveInRecordRepository,
                           UserRepository userRepository) {
        this.residentRepository         = residentRepository;
        this.emergencyContactRepository = emergencyContactRepository;
        this.moveInRecordRepository     = moveInRecordRepository;
        this.userRepository             = userRepository;
    }

    // ── Directory ─────────────────────────────────────────────────────────────

    public Page<Resident> findAll(Pageable pageable) {
        return residentRepository.findAll(pageable);
    }

    public Page<Resident> search(String q, String building, Integer classYear,
                                 CheckInStatus moveInStatus, Pageable pageable) {
        Specification<Resident> spec = Specification
                .where(ResidentSpec.withSearch(q))
                .and(ResidentSpec.withBuilding(building))
                .and(ResidentSpec.withClassYear(classYear))
                .and(ResidentSpec.withMoveInStatus(moveInStatus));
        return residentRepository.findAll(spec, pageable);
    }

    public FilterOptionsResponse filterOptions() {
        return new FilterOptionsResponse(
                residentRepository.findDistinctBuildingNames(),
                residentRepository.findDistinctClassYears()
        );
    }

    // ── Lookups ───────────────────────────────────────────────────────────────

    public Resident findById(UUID id) {
        return residentRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Resident not found: " + id));
    }

    public Resident findByStudentId(String studentId) {
        return residentRepository.findByStudentId(studentId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Resident not found with studentId: " + studentId));
    }

    public Resident findByEmail(String email) {
        return residentRepository.findByEmail(email)
                .orElseThrow(() -> new EntityNotFoundException(
                        "No resident record is linked to this account"));
    }

    public Resident findByLinkedUserId(UUID userId) {
        return residentRepository.findByLinkedUserId(userId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "No resident record is linked to this account"));
    }

    // ── Create ────────────────────────────────────────────────────────────────

    /**
     * Creates a new resident.
     *
     * @param force  when {@code true}, skips the duplicate pre-check and proceeds
     *               regardless of potential matches (caller has acknowledged the warning)
     * @throws DuplicateResidentException when duplicates are found and {@code force} is false
     * @throws IllegalArgumentException   when the email is already in use by a different resident
     */
    @Transactional
    public Resident create(ResidentRequest req, boolean force) {
        if (!force) {
            DuplicateCheckResponse dupes = checkDuplicates(
                    req.firstName(), req.lastName(), req.email(), req.studentId(), req.dateOfBirth(), null);
            if (dupes.hasDuplicates()) {
                throw new DuplicateResidentException(dupes);
            }
        }

        if (residentRepository.existsByEmail(req.email())) {
            throw new IllegalArgumentException("Email address is already in use");
        }
        if (req.studentId() != null && !req.studentId().isBlank()
                && residentRepository.existsByStudentId(req.studentId())) {
            throw new IllegalArgumentException("Student ID is already in use");
        }

        Resident resident = new Resident();
        applyRequest(resident, req);
        applyLinkedUser(resident, req.email());
        return residentRepository.save(resident);
    }

    // ── Update ────────────────────────────────────────────────────────────────

    @Transactional
    public Resident update(UUID id, ResidentRequest req) {
        Resident resident = findById(id);

        if (residentRepository.existsByEmailIgnoreCaseAndIdNot(req.email(), id)) {
            throw new IllegalArgumentException("Email address is already in use");
        }
        if (req.studentId() != null && !req.studentId().isBlank()
                && residentRepository.existsByStudentIdAndIdNot(req.studentId(), id)) {
            throw new IllegalArgumentException("Student ID is already in use");
        }

        applyRequest(resident, req);
        applyLinkedUser(resident, req.email());
        return residentRepository.save(resident);
    }

    private void applyRequest(Resident r, ResidentRequest req) {
        r.setFirstName(req.firstName());
        r.setLastName(req.lastName());
        r.setEmail(req.email());
        r.setPhone(req.phone());
        r.setStudentId(req.studentId());
        r.setDateOfBirth(req.dateOfBirth());
        r.setEnrollmentStatus(req.enrollmentStatus());
        r.setDepartment(req.department());
        r.setClassYear(req.classYear());
        r.setRoomNumber(req.roomNumber());
        r.setBuildingName(req.buildingName());
    }

    /**
     * Auto-links a resident to a STUDENT account the first time an exact email match exists.
     * Once linked, the relationship remains stable even if the resident email later changes.
     */
    private void applyLinkedUser(Resident resident, String email) {
        if (resident.getLinkedUser() != null || email == null || email.isBlank()) {
            return;
        }
        userRepository.findByEmailIgnoreCase(email.strip())
                .filter(this::isStudentAccount)
                .ifPresent(resident::setLinkedUser);
    }

    private boolean isStudentAccount(User user) {
        return user.getUserRoles().stream()
                .anyMatch(ur -> ur.getRole().getName() == RoleName.STUDENT);
    }

    // ── Duplicate detection ───────────────────────────────────────────────────

    /**
     * Checks whether any existing residents might be duplicates.
     *
     * <p>Three signals are checked independently:
     * <ol>
     *   <li><b>email</b>   — exact case-insensitive match (unique in DB, at most 1 hit)</li>
     *   <li><b>studentId</b> — exact match</li>
     *   <li><b>name</b>    — same first + last name, case-insensitive</li>
     * </ol>
     * Each matching resident appears at most once in the list (earliest signal wins).
     *
     * @param excludeId  when editing an existing record, pass its ID so it is
     *                   not reported as its own duplicate
     */
    public DuplicateCheckResponse checkDuplicates(
            String firstName, String lastName, String email, String studentId,
            LocalDate dateOfBirth, UUID excludeId) {

        List<DuplicateCheckResponse.Candidate> candidates = new ArrayList<>();

        if (studentId != null && !studentId.isBlank()) {
            residentRepository.findByStudentId(studentId.trim()).ifPresent(r -> {
                if ((excludeId == null || !r.getId().equals(excludeId))
                        && noneMatch(candidates, r.getId())) {
                    candidates.add(candidate(r, "studentId"));
                }
            });
        }

        if (firstName != null && !firstName.isBlank()
                && lastName != null && !lastName.isBlank()
                && dateOfBirth != null) {
            residentRepository
                    .findByFirstNameIgnoreCaseAndLastNameIgnoreCase(firstName.trim(), lastName.trim())
                    .stream()
                    .filter(r -> dateOfBirth.equals(r.getDateOfBirth()))
                    .filter(r -> excludeId == null || !r.getId().equals(excludeId))
                    .filter(r -> noneMatch(candidates, r.getId()))
                    .forEach(r -> candidates.add(candidate(r, "name+dob")));
        }

        return new DuplicateCheckResponse(candidates);
    }

    private static DuplicateCheckResponse.Candidate candidate(Resident r, String reason) {
        return new DuplicateCheckResponse.Candidate(
                r.getId(), r.getStudentId(), r.getFirstName(), r.getLastName(), r.getEmail(), reason);
    }

    private static boolean noneMatch(List<DuplicateCheckResponse.Candidate> list, UUID id) {
        return list.stream().noneMatch(c -> c.id().equals(id));
    }

    // ── Emergency contacts ────────────────────────────────────────────────────

    public List<EmergencyContact> findEmergencyContacts(UUID residentId) {
        findById(residentId); // verify resident exists
        return emergencyContactRepository.findByResidentId(residentId);
    }

    @Transactional
    public EmergencyContact addEmergencyContact(UUID residentId, EmergencyContactRequest req) {
        Resident resident = findById(residentId);
        // Only one primary contact allowed
        if (req.primary()) {
            emergencyContactRepository.findByResidentIdAndPrimaryTrue(residentId)
                    .forEach(c -> { c.setPrimary(false); emergencyContactRepository.save(c); });
        }
        EmergencyContact ec = new EmergencyContact();
        ec.setResident(resident);
        applyContactRequest(ec, req);
        return emergencyContactRepository.save(ec);
    }

    @Transactional
    public EmergencyContact updateEmergencyContact(UUID residentId, UUID contactId,
                                                    EmergencyContactRequest req) {
        EmergencyContact ec = emergencyContactRepository.findById(contactId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Emergency contact not found: " + contactId));
        if (!ec.getResident().getId().equals(residentId)) {
            throw new IllegalArgumentException("Contact does not belong to resident " + residentId);
        }
        if (req.primary() && !ec.isPrimary()) {
            emergencyContactRepository.findByResidentIdAndPrimaryTrue(residentId)
                    .forEach(c -> { c.setPrimary(false); emergencyContactRepository.save(c); });
        }
        applyContactRequest(ec, req);
        return emergencyContactRepository.save(ec);
    }

    @Transactional
    public void removeEmergencyContact(UUID residentId, UUID contactId) {
        EmergencyContact ec = emergencyContactRepository.findById(contactId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Emergency contact not found: " + contactId));
        if (!ec.getResident().getId().equals(residentId)) {
            throw new IllegalArgumentException("Contact does not belong to resident " + residentId);
        }
        emergencyContactRepository.delete(ec);
    }

    private void applyContactRequest(EmergencyContact ec, EmergencyContactRequest req) {
        ec.setName(req.name());
        ec.setRelationship(req.relationship());
        ec.setPhone(req.phone());
        ec.setEmail(req.email());
        ec.setPrimary(req.primary());
    }

    // ── Move-in records ───────────────────────────────────────────────────────

    public List<MoveInRecord> findMoveInRecords(UUID residentId) {
        findById(residentId);
        return moveInRecordRepository.findByResidentId(residentId);
    }

    @Transactional
    public MoveInRecord addMoveInRecord(UUID residentId, MoveInRecordRequest req) {
        Resident resident = findById(residentId);
        MoveInRecord record = new MoveInRecord();
        record.setResident(resident);
        applyMoveInRequest(record, req);
        MoveInRecord saved = moveInRecordRepository.save(record);
        // Sync current room on the resident
        resident.setRoomNumber(record.getRoomNumber());
        resident.setBuildingName(record.getBuildingName());
        residentRepository.save(resident);
        return saved;
    }

    @Transactional
    public MoveInRecord updateMoveInRecord(UUID residentId, UUID recordId, MoveInRecordRequest req) {
        MoveInRecord record = moveInRecordRepository.findById(recordId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Move-in record not found: " + recordId));
        if (!record.getResident().getId().equals(residentId)) {
            throw new IllegalArgumentException(
                    "Move-in record does not belong to resident " + residentId);
        }
        applyMoveInRequest(record, req);
        return moveInRecordRepository.save(record);
    }

    private void applyMoveInRequest(MoveInRecord r, MoveInRecordRequest req) {
        r.setRoomNumber(req.roomNumber());
        r.setBuildingName(req.buildingName());
        r.setMoveInDate(req.moveInDate());
        r.setMoveOutDate(req.moveOutDate());
        r.setCheckInStatus(req.checkInStatus() != null
                ? req.checkInStatus()
                : com.reslife.api.domain.housing.CheckInStatus.PENDING);
        r.setNotes(req.notes());
    }

    // ── Soft delete ───────────────────────────────────────────────────────────

    @Transactional
    public Resident save(Resident resident) {
        return residentRepository.save(resident);
    }

    @Transactional
    public void softDelete(UUID id) {
        Resident resident = findById(id);
        resident.softDelete();
        residentRepository.save(resident);
    }

    /** @deprecated kept for backward compat with HousingService — use addMoveInRecord */
    @Transactional
    public EmergencyContact addEmergencyContact(UUID residentId, EmergencyContact contact) {
        Resident resident = findById(residentId);
        contact.setResident(resident);
        return emergencyContactRepository.save(contact);
    }
}
