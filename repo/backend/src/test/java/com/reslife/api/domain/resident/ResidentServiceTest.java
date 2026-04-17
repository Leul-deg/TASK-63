package com.reslife.api.domain.resident;

import com.reslife.api.common.BaseEntity;
import com.reslife.api.domain.housing.MoveInRecordRepository;
import com.reslife.api.domain.user.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ResidentServiceTest {

    private final ResidentRepository           residentRepository           = mock(ResidentRepository.class);
    private final EmergencyContactRepository   emergencyContactRepository   = mock(EmergencyContactRepository.class);
    private final MoveInRecordRepository       moveInRecordRepository       = mock(MoveInRecordRepository.class);
    private final UserRepository               userRepository               = mock(UserRepository.class);

    private ResidentService service;

    @BeforeEach
    void setUp() {
        service = new ResidentService(
                residentRepository,
                emergencyContactRepository,
                moveInRecordRepository,
                userRepository);
    }

    // ── findById ──────────────────────────────────────────────────────────────

    @Test
    void findById_throwsEntityNotFoundException_whenNotFound() {
        UUID id = UUID.randomUUID();
        when(residentRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> service.findById(id));
    }

    @Test
    void findById_returnsResident_whenFound() {
        UUID id = UUID.randomUUID();
        Resident r = new Resident();
        assignId(r, id);
        when(residentRepository.findById(id)).thenReturn(Optional.of(r));

        assertSame(r, service.findById(id));
    }

    // ── create ────────────────────────────────────────────────────────────────

    @Test
    void create_throwsDuplicateResidentException_whenDuplicateFoundAndForceIsFalse() {
        ResidentRequest req = new ResidentRequest(
                "Alice", "Smith", "alice@test.com", "555-000-0001",
                "S100", LocalDate.of(2000, 1, 1),
                null, null, null, null, null);

        // Simulate duplicate found by studentId
        Resident existing = new Resident();
        assignId(existing, UUID.randomUUID());
        existing.setStudentId("S100");
        existing.setFirstName("Alice");
        existing.setLastName("Smith");
        existing.setEmail("alice@test.com");
        when(residentRepository.findByStudentId("S100")).thenReturn(Optional.of(existing));
        when(residentRepository.existsByEmail("alice@test.com")).thenReturn(false);

        assertThrows(DuplicateResidentException.class, () -> service.create(req, false));
    }

    @Test
    void create_throwsIllegalArgumentException_whenEmailAlreadyInUse() {
        ResidentRequest req = new ResidentRequest(
                "Bob", "Jones", "taken@test.com", "555-000-0002",
                null, LocalDate.of(1999, 5, 5),
                null, null, null, null, null);

        when(residentRepository.existsByEmail("taken@test.com")).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> service.create(req, true));
    }

    @Test
    void create_succeedsWithForceTrue_evenWhenDuplicatesExist() {
        ResidentRequest req = new ResidentRequest(
                "Carol", "Lee", "carol@test.com", "555-000-0003",
                "S200", LocalDate.of(2001, 3, 15),
                null, null, null, null, null);

        when(residentRepository.existsByEmail("carol@test.com")).thenReturn(false);
        when(residentRepository.save(any(Resident.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.findByEmailIgnoreCase("carol@test.com")).thenReturn(Optional.empty());

        Resident result = service.create(req, true);

        assertNotNull(result);
        verify(residentRepository).save(any(Resident.class));
    }

    // ── update ────────────────────────────────────────────────────────────────

    @Test
    void update_throwsIllegalArgumentException_whenEmailUsedByAnotherResident() {
        UUID id = UUID.randomUUID();
        Resident existing = new Resident();
        assignId(existing, id);
        when(residentRepository.findById(id)).thenReturn(Optional.of(existing));
        when(residentRepository.existsByEmailIgnoreCaseAndIdNot("conflict@test.com", id))
                .thenReturn(true);

        ResidentRequest req = new ResidentRequest(
                "Dan", "Brown", "conflict@test.com", "555-000-0004",
                null, null, null, null, null, null, null);

        assertThrows(IllegalArgumentException.class, () -> service.update(id, req));
    }

    // ── addEmergencyContact ───────────────────────────────────────────────────

    @Test
    void addEmergencyContact_demotesExistingPrimary_whenNewContactIsPrimary() {
        UUID residentId = UUID.randomUUID();
        Resident resident = new Resident();
        assignId(resident, residentId);
        when(residentRepository.findById(residentId)).thenReturn(Optional.of(resident));

        EmergencyContact oldPrimary = mock(EmergencyContact.class);
        when(emergencyContactRepository.findByResidentIdAndPrimaryTrue(residentId))
                .thenReturn(List.of(oldPrimary));
        when(emergencyContactRepository.save(any(EmergencyContact.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        EmergencyContactRequest req = new EmergencyContactRequest(
                "Jane Doe", "Mother", "555-123-4567", "jane@test.com", true);

        service.addEmergencyContact(residentId, req);

        verify(oldPrimary).setPrimary(false);
        verify(emergencyContactRepository).save(oldPrimary);
    }

    // ── updateEmergencyContact ────────────────────────────────────────────────

    @Test
    void updateEmergencyContact_throwsIllegalArgumentException_whenContactBelongsToDifferentResident() {
        UUID residentId  = UUID.randomUUID();
        UUID contactId   = UUID.randomUUID();
        UUID otherResident = UUID.randomUUID();

        Resident other = new Resident();
        assignId(other, otherResident);

        EmergencyContact ec = new EmergencyContact();
        ec.setResident(other);
        when(emergencyContactRepository.findById(contactId)).thenReturn(Optional.of(ec));

        EmergencyContactRequest req = new EmergencyContactRequest(
                "Jane Doe", "Mother", "555-123-4567", null, false);

        assertThrows(IllegalArgumentException.class,
                () -> service.updateEmergencyContact(residentId, contactId, req));
    }

    // ── removeEmergencyContact ────────────────────────────────────────────────

    @Test
    void removeEmergencyContact_throwsIllegalArgumentException_whenContactBelongsToDifferentResident() {
        UUID residentId    = UUID.randomUUID();
        UUID contactId     = UUID.randomUUID();
        UUID otherResident = UUID.randomUUID();

        Resident other = new Resident();
        assignId(other, otherResident);

        EmergencyContact ec = new EmergencyContact();
        ec.setResident(other);
        when(emergencyContactRepository.findById(contactId)).thenReturn(Optional.of(ec));

        assertThrows(IllegalArgumentException.class,
                () -> service.removeEmergencyContact(residentId, contactId));
    }

    @Test
    void removeEmergencyContact_deletesContact_whenOwnershipMatches() {
        UUID residentId = UUID.randomUUID();
        UUID contactId  = UUID.randomUUID();

        Resident resident = new Resident();
        assignId(resident, residentId);

        EmergencyContact ec = new EmergencyContact();
        ec.setResident(resident);
        when(emergencyContactRepository.findById(contactId)).thenReturn(Optional.of(ec));

        service.removeEmergencyContact(residentId, contactId);

        verify(emergencyContactRepository).delete(ec);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static void assignId(Object entity, UUID id) {
        try {
            Field idField = BaseEntity.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(entity, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
