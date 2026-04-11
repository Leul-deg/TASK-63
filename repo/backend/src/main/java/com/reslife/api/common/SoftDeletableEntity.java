package com.reslife.api.common;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;

import java.time.Instant;

/**
 * Extends BaseEntity with a deletedAt field for soft-delete support.
 * Each concrete entity must also carry @SQLRestriction("deleted_at IS NULL")
 * so that Hibernate automatically excludes deleted rows from queries.
 */
@Getter
@MappedSuperclass
public abstract class SoftDeletableEntity extends BaseEntity {

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public void softDelete() {
        this.deletedAt = Instant.now();
    }
}
