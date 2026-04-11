package com.reslife.api.domain.user;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);
    Optional<User> findByEmailIgnoreCase(String email);
    Optional<User> findByUsername(String username);
    Optional<User> findByUsernameIgnoreCase(String username);

    boolean existsByEmail(String email);
    boolean existsByUsername(String username);

    @Query("SELECT u FROM User u JOIN u.userRoles ur JOIN ur.role r WHERE r.name = :roleName")
    List<User> findAllByRoleName(RoleName roleName);

    /** Search users by name/username for the new-thread user picker. */
    @Query("SELECT u FROM User u WHERE u.id != :excludeId AND (" +
           "LOWER(u.firstName) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           "LOWER(u.lastName)  LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           "LOWER(u.username)  LIKE LOWER(CONCAT('%', :q, '%')))")
    List<User> searchUsers(@Param("q") String q, @Param("excludeId") UUID excludeId, Pageable pageable);

    @Query("SELECT DISTINCT u FROM User u JOIN u.userRoles ur JOIN ur.role r " +
           "WHERE u.id != :excludeId AND r.name IN :roleNames AND (" +
           "LOWER(u.firstName) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           "LOWER(u.lastName)  LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           "LOWER(u.username)  LIKE LOWER(CONCAT('%', :q, '%')))")
    List<User> searchUsersByRoleNames(@Param("q") String q,
                                      @Param("excludeId") UUID excludeId,
                                      @Param("roleNames") Set<RoleName> roleNames,
                                      Pageable pageable);

    // -----------------------------------------------------------------------
    // Queries that bypass @SQLRestriction("deleted_at IS NULL")
    // Required for purge scheduling and admin operations on soft-deleted rows.
    // -----------------------------------------------------------------------

    /**
     * Find users whose soft-delete grace period has expired and are ready for hard deletion.
     * Must use nativeQuery to bypass the @SQLRestriction on User.
     */
    @Query(value = "SELECT * FROM users WHERE deleted_at IS NOT NULL AND scheduled_purge_at <= :now",
           nativeQuery = true)
    List<User> findUsersReadyForPurge(Instant now);

    /**
     * Hard-delete users past their scheduled purge date.
     */
    @Modifying
    @Query(value = "DELETE FROM users WHERE deleted_at IS NOT NULL AND scheduled_purge_at <= :now",
           nativeQuery = true)
    int hardDeletePurgeableUsers(Instant now);
}
