package com.reslife.api.domain.resident;

import com.reslife.api.domain.housing.MoveInRecordRepository;
import com.reslife.api.domain.user.Role;
import com.reslife.api.domain.user.RoleName;
import com.reslife.api.domain.user.User;
import com.reslife.api.domain.user.UserRepository;
import com.reslife.api.domain.user.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ResidentServiceLinkingTest {

    private final ResidentRepository residentRepository = mock(ResidentRepository.class);
    private final EmergencyContactRepository emergencyContactRepository = mock(EmergencyContactRepository.class);
    private final MoveInRecordRepository moveInRecordRepository = mock(MoveInRecordRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);

    private ResidentService service;

    @BeforeEach
    void setUp() {
        service = new ResidentService(
                residentRepository,
                emergencyContactRepository,
                moveInRecordRepository,
                userRepository);
        when(residentRepository.save(any(Resident.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void create_linksResidentToMatchingStudentAccount() {
        User studentUser = buildUser(RoleName.STUDENT);
        when(userRepository.findByEmailIgnoreCase("student@reslife.local"))
                .thenReturn(Optional.of(studentUser));

        Resident created = service.create(request("student@reslife.local"), true);

        assertSame(studentUser, created.getLinkedUser());
    }

    @Test
    void update_preservesExistingLinkEvenWhenResidentEmailChanges() {
        UUID residentId = UUID.randomUUID();
        User linkedStudent = buildUser(RoleName.STUDENT);

        Resident existing = new Resident();
        assignId(existing, residentId);
        existing.setEmail("student@reslife.local");
        existing.setLinkedUser(linkedStudent);

        when(residentRepository.findById(residentId)).thenReturn(Optional.of(existing));
        when(residentRepository.existsByEmailIgnoreCaseAndIdNot("new-address@campus.edu", residentId))
                .thenReturn(false);

        Resident updated = service.update(residentId, request("new-address@campus.edu"));

        assertEquals("new-address@campus.edu", updated.getEmail());
        assertSame(linkedStudent, updated.getLinkedUser());
    }

    private ResidentRequest request(String email) {
        return new ResidentRequest(
                "Alex",
                "Chen",
                email,
                "555-123-4567",
                "S-200",
                null,
                "ENROLLED",
                "Computer Science",
                2027,
                "101",
                "Maple Hall"
        );
    }

    private User buildUser(RoleName roleName) {
        Role role = mock(Role.class);
        when(role.getName()).thenReturn(roleName);

        UserRole userRole = mock(UserRole.class);
        when(userRole.getRole()).thenReturn(role);

        User user = mock(User.class);
        when(user.getUserRoles()).thenReturn(Set.of(userRole));
        return user;
    }

    private static void assignId(Object entity, UUID id) {
        try {
            java.lang.reflect.Field idField =
                    com.reslife.api.common.BaseEntity.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(entity, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
