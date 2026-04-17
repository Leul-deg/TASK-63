package com.reslife.api.domain.user;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.test.context.TestPropertySource;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JPA-layer integration tests for {@link UserRepository}.
 *
 * <p>Exercises JPQL queries (role-based search, text search) and native queries
 * that bypass the {@code @SQLRestriction("deleted_at IS NULL")} filter
 * (purge eligibility, hard-delete).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:reslife_user;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DEFAULT_NULL_ORDERING=HIGH",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "app.encryption.key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
    "spring.session.store-type=none",
    "spring.jpa.show-sql=false"
})
class UserRepositoryTest {

    @Autowired private UserRepository userRepo;
    @Autowired private RoleRepository roleRepo;
    @Autowired private TestEntityManager em;

    // ── Helpers ───────────────────────────────────────────────────────────────

    private User savedUser(String email, String username) {
        User u = new User();
        u.setEmail(email);
        u.setUsername(username);
        u.setPasswordHash("$2a$10$hash");
        u.setFirstName(username);
        u.setLastName("Test");
        u.setAccountStatus(AccountStatus.ACTIVE);
        return em.persistAndFlush(u);
    }

    // ── findByEmail / findByUsername ──────────────────────────────────────────

    @Test
    void findByEmail_returnsUser_whenEmailMatches() {
        savedUser("find@test.com", "finduser");
        assertThat(userRepo.findByEmail("find@test.com")).isPresent();
    }

    @Test
    void findByEmail_returnsEmpty_whenEmailNotFound() {
        assertThat(userRepo.findByEmail("nobody@test.com")).isEmpty();
    }

    @Test
    void findByUsername_returnsUser_whenUsernameMatches() {
        savedUser("name@test.com", "nameuser");
        assertThat(userRepo.findByUsername("nameuser")).isPresent();
    }

    @Test
    void findByUsername_returnsEmpty_whenUsernameNotFound() {
        assertThat(userRepo.findByUsername("ghost")).isEmpty();
    }

    // ── existsByEmail / existsByUsername ──────────────────────────────────────

    @Test
    void existsByEmail_returnsTrue_whenUserExists() {
        savedUser("exists@test.com", "existsuser");
        assertThat(userRepo.existsByEmail("exists@test.com")).isTrue();
    }

    @Test
    void existsByEmail_returnsFalse_whenNoUserWithThatEmail() {
        assertThat(userRepo.existsByEmail("none@test.com")).isFalse();
    }

    @Test
    void existsByUsername_returnsTrue_whenUserExists() {
        savedUser("uname@test.com", "unameuser");
        assertThat(userRepo.existsByUsername("unameuser")).isTrue();
    }

    // ── searchUsers ───────────────────────────────────────────────────────────

    @Test
    void searchUsers_returnsMatchingUsers_byFirstName() {
        User alice  = savedUser("alice@test.com", "alicesmith");
        User bob    = savedUser("bob@test.com",   "bobsmith");
        UUID excludeId = bob.getId();

        List<User> results = userRepo.searchUsers("alice", excludeId, PageRequest.of(0, 10));

        assertThat(results).extracting(User::getUsername).containsExactly("alicesmith");
    }

    @Test
    void searchUsers_excludesSpecifiedUser() {
        User u1 = savedUser("sr1@test.com", "searchone");
        User u2 = savedUser("sr2@test.com", "searchtwo");

        List<User> results = userRepo.searchUsers("search", u1.getId(), PageRequest.of(0, 10));

        assertThat(results).extracting(User::getUsername).doesNotContain("searchone");
    }

    // ── findAllByRoleName ─────────────────────────────────────────────────────

    @Test
    void findAllByRoleName_returnsUsersWithMatchingRole() {
        Role staffRole = new Role(RoleName.RESIDENCE_STAFF, "Staff");
        roleRepo.save(staffRole);

        User staffUser  = savedUser("staff-role@test.com", "staffrole");
        User otherUser  = savedUser("other-role@test.com", "otherrole");

        UserRole ur = new UserRole(staffUser, staffRole);
        staffUser.getUserRoles().add(ur);
        em.persistAndFlush(ur);

        List<User> results = userRepo.findAllByRoleName(RoleName.RESIDENCE_STAFF);

        assertThat(results).extracting(User::getUsername).contains("staffrole");
        assertThat(results).extracting(User::getUsername).doesNotContain("otherrole");
    }

    // ── findUsersReadyForPurge / hardDeletePurgeableUsers ─────────────────────

    @Test
    void findUsersReadyForPurge_returnsUserWithExpiredPurgeDate() {
        User user = savedUser("purge@test.com", "purgeuser");

        // Bypass @SQLRestriction by setting deleted_at and scheduled_purge_at via native SQL
        Instant pastDate = Instant.now().minus(2, ChronoUnit.DAYS);
        em.getEntityManager().createNativeQuery(
                "UPDATE users SET deleted_at = ?, scheduled_purge_at = ? WHERE id = ?")
                .setParameter(1, Timestamp.from(Instant.now()))
                .setParameter(2, Timestamp.from(pastDate))
                .setParameter(3, user.getId())
                .executeUpdate();
        em.flush();

        List<User> ready = userRepo.findUsersReadyForPurge(Instant.now());

        assertThat(ready).anyMatch(u -> u.getId().equals(user.getId()));
    }

    @Test
    void findUsersReadyForPurge_doesNotReturnUserWithFuturePurgeDate() {
        User user = savedUser("future@test.com", "futureuser");

        Instant futureDate = Instant.now().plus(10, ChronoUnit.DAYS);
        em.getEntityManager().createNativeQuery(
                "UPDATE users SET deleted_at = ?, scheduled_purge_at = ? WHERE id = ?")
                .setParameter(1, Timestamp.from(Instant.now()))
                .setParameter(2, Timestamp.from(futureDate))
                .setParameter(3, user.getId())
                .executeUpdate();
        em.flush();

        List<User> ready = userRepo.findUsersReadyForPurge(Instant.now());

        assertThat(ready).noneMatch(u -> u.getId().equals(user.getId()));
    }

    @Test
    void hardDeletePurgeableUsers_removesExpiredRowsAndReturnsCount() {
        User user = savedUser("harddelete@test.com", "harddeleteuser");

        Instant pastDate = Instant.now().minus(31, ChronoUnit.DAYS);
        em.getEntityManager().createNativeQuery(
                "UPDATE users SET deleted_at = ?, scheduled_purge_at = ? WHERE id = ?")
                .setParameter(1, Timestamp.from(Instant.now()))
                .setParameter(2, Timestamp.from(pastDate))
                .setParameter(3, user.getId())
                .executeUpdate();
        em.flush();

        int deleted = userRepo.hardDeletePurgeableUsers(Instant.now());

        assertThat(deleted).isGreaterThanOrEqualTo(1);
    }

    @TestConfiguration
    @EnableJpaAuditing
    static class AuditingConfig {}
}
