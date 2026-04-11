package com.reslife.api.domain.messaging;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * A student's block on a specific staff member.
 *
 * <p>When a block exists, the blocked staff user cannot initiate new
 * {@link ThreadType#DIRECT} threads with that student.
 * Staff can still send {@link ThreadType#SYSTEM_NOTICE} messages regardless of blocks.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "staff_blocks")
public class StaffBlock {

    @EmbeddedId
    private StaffBlockId id;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public StaffBlock(UUID studentUserId, UUID blockedStaffUserId) {
        this.id = new StaffBlockId(studentUserId, blockedStaffUserId);
        this.createdAt = Instant.now();
    }
}
