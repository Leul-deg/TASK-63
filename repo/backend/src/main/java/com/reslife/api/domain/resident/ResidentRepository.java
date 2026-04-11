package com.reslife.api.domain.resident;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ResidentRepository extends JpaRepository<Resident, UUID>,
        JpaSpecificationExecutor<Resident> {

    Optional<Resident> findByEmail(String email);
    Optional<Resident> findByStudentId(String studentId);
    Optional<Resident> findByLinkedUserId(UUID userId);
    boolean existsByEmail(String email);
    boolean existsByStudentId(String studentId);
    Page<Resident> findByLastNameContainingIgnoreCase(String lastName, Pageable pageable);
    Page<Resident> findByBuildingName(String buildingName, Pageable pageable);
    Page<Resident> findByRoomNumberAndBuildingName(String roomNumber, String buildingName, Pageable pageable);

    /** Distinct non-null building names for the filter dropdown, sorted alphabetically. */
    @Query("SELECT DISTINCT r.buildingName FROM Resident r WHERE r.buildingName IS NOT NULL ORDER BY r.buildingName")
    List<String> findDistinctBuildingNames();

    /** Distinct non-null class years for the filter dropdown, sorted ascending. */
    @Query("SELECT DISTINCT r.classYear FROM Resident r WHERE r.classYear IS NOT NULL ORDER BY r.classYear")
    List<Integer> findDistinctClassYears();

    // --- Duplicate detection ---

    /** Case-insensitive email lookup (email is unique so at most one result). */
    Optional<Resident> findByEmailIgnoreCase(String email);

    /** All active residents with the same first + last name (case-insensitive). */
    List<Resident> findByFirstNameIgnoreCaseAndLastNameIgnoreCase(String firstName, String lastName);

    boolean existsByEmailIgnoreCaseAndIdNot(String email, UUID excludeId);
    boolean existsByStudentIdAndIdNot(String studentId, UUID excludeId);
}
