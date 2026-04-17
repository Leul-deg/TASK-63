package com.reslife.api.domain.user;

import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class UserServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final RoleRepository roleRepository = mock(RoleRepository.class);
    private UserService service;

    private static final UUID USER_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new UserService(userRepository, roleRepository);
    }

    // ── findAll ───────────────────────────────────────────────────────────────

    @Test
    void findAll_returnsRepositoryResult() {
        User user = new User();
        when(userRepository.findAll()).thenReturn(List.of(user));
        assertThat(service.findAll()).containsExactly(user);
    }

    // ── findById ──────────────────────────────────────────────────────────────

    @Test
    void findById_returnsUser_whenFound() {
        User user = new User();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        assertThat(service.findById(USER_ID)).isSameAs(user);
    }

    @Test
    void findById_throwsEntityNotFoundException_whenNotFound() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(USER_ID))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining(USER_ID.toString());
    }

    // ── findByEmail ───────────────────────────────────────────────────────────

    @Test
    void findByEmail_returnsUser_whenFound() {
        User user = new User();
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        assertThat(service.findByEmail("test@example.com")).isSameAs(user);
    }

    @Test
    void findByEmail_throwsEntityNotFoundException_whenNotFound() {
        when(userRepository.findByEmail("no@one.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findByEmail("no@one.com"))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("no@one.com");
    }

    // ── save ──────────────────────────────────────────────────────────────────

    @Test
    void save_delegatesToRepository() {
        User user = new User();
        when(userRepository.save(user)).thenReturn(user);
        assertThat(service.save(user)).isSameAs(user);
    }

    // ── assignRole ────────────────────────────────────────────────────────────

    @Test
    void assignRole_addsRoleToUserAndSaves() {
        User user = new User();
        Role role = new Role(RoleName.RESIDENCE_STAFF, "Staff");
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(roleRepository.findByName(RoleName.RESIDENCE_STAFF)).thenReturn(Optional.of(role));
        when(userRepository.save(user)).thenReturn(user);

        service.assignRole(USER_ID, RoleName.RESIDENCE_STAFF);

        assertThat(user.getUserRoles()).hasSize(1);
        verify(userRepository).save(user);
    }

    @Test
    void assignRole_throwsWhenRoleNotFound() {
        User user = new User();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(roleRepository.findByName(RoleName.RESIDENCE_STAFF)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.assignRole(USER_ID, RoleName.RESIDENCE_STAFF))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("RESIDENCE_STAFF");
    }

    // ── softDelete ────────────────────────────────────────────────────────────

    @Test
    void softDelete_setsDeletedAtAndDisablesAccount() {
        User user = new User();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        service.softDelete(USER_ID);

        assertThat(user.isDeleted()).isTrue();
        assertThat(user.getAccountStatus()).isEqualTo(AccountStatus.DISABLED);
        assertThat(user.getScheduledPurgeAt()).isAfter(Instant.now().minusSeconds(10));
        verify(userRepository).save(user);
    }

    @Test
    void softDelete_throwsWhenUserNotFound() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.softDelete(USER_ID))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining(USER_ID.toString());
    }
}
