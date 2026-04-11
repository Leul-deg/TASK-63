package com.reslife.api.domain.messaging;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;

import java.io.Serializable;
import java.util.UUID;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class StaffBlockId implements Serializable {

    @Column(name = "student_user_id", nullable = false)
    private UUID studentUserId;

    @Column(name = "blocked_staff_user_id", nullable = false)
    private UUID blockedStaffUserId;
}
