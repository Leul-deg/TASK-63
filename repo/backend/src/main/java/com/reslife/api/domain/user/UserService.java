package com.reslife.api.domain.user;

import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;

    public UserService(UserRepository userRepository, RoleRepository roleRepository) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
    }

    public List<User> findAll() {
        return userRepository.findAll();
    }

    public User findById(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + id));
    }

    public User findByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new EntityNotFoundException("User not found with email: " + email));
    }

    @Transactional
    public User save(User user) {
        return userRepository.save(user);
    }

    @Transactional
    public User assignRole(UUID userId, RoleName roleName) {
        User user = findById(userId);
        Role role = roleRepository.findByName(roleName)
                .orElseThrow(() -> new EntityNotFoundException("Role not found: " + roleName));
        user.getUserRoles().add(new UserRole(user, role));
        return userRepository.save(user);
    }

    /**
     * Soft-deletes a user and schedules hard deletion 30 days from now.
     * Use {@link com.reslife.api.admin.AdminUserService#updateAccountStatus} for
     * admin-driven status changes — this method is for programmatic deletion only.
     */
    @Transactional
    public void softDelete(UUID id) {
        User user = findById(id);
        user.softDelete();
        user.setAccountStatus(AccountStatus.DISABLED);
        user.setScheduledPurgeAt(java.time.Instant.now()
                .plus(30, java.time.temporal.ChronoUnit.DAYS));
        userRepository.save(user);
    }
}
