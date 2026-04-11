package com.reslife.api.domain.resident;

import com.reslife.api.domain.housing.CheckInStatus;
import com.reslife.api.domain.housing.MoveInRecord;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

/**
 * Composable JPA Specifications for resident directory filtering.
 *
 * <p>All specs are null-safe: pass {@code null} to skip a filter.
 * Chain them with {@link Specification#where} + {@link Specification#and}.
 */
public final class ResidentSpec {

    private ResidentSpec() {}

    /**
     * Fuzzy text search across first name, last name, email, and student ID.
     * Encrypted fields (dateOfBirth) are excluded — they cannot be searched
     * in their ciphertext form.
     */
    public static Specification<Resident> withSearch(String q) {
        if (q == null || q.isBlank()) return null;
        String pattern = "%" + q.toLowerCase() + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("firstName")), pattern),
                cb.like(cb.lower(root.get("lastName")),  pattern),
                cb.like(cb.lower(root.get("email")),     pattern),
                cb.like(cb.lower(root.get("studentId")), pattern)
        );
    }

    /** Exact match on building name (case-sensitive, as stored). */
    public static Specification<Resident> withBuilding(String building) {
        if (building == null || building.isBlank()) return null;
        return (root, query, cb) -> cb.equal(root.get("buildingName"), building);
    }

    /** Exact match on class year. */
    public static Specification<Resident> withClassYear(Integer classYear) {
        if (classYear == null) return null;
        return (root, query, cb) -> cb.equal(root.get("classYear"), classYear);
    }

    /**
     * Filters residents who have at least one move_in_record with the given
     * {@link CheckInStatus}.  Uses an EXISTS subquery to avoid row duplication
     * when residents have multiple records.
     */
    public static Specification<Resident> withMoveInStatus(CheckInStatus status) {
        if (status == null) return null;
        return (root, query, cb) -> {
            Subquery<MoveInRecord> sub = query.subquery(MoveInRecord.class);
            var mir = sub.from(MoveInRecord.class);
            sub.select(mir)
               .where(
                   cb.equal(mir.get("resident"), root),
                   cb.equal(mir.get("checkInStatus"), status)
               );
            return cb.exists(sub);
        };
    }
}
