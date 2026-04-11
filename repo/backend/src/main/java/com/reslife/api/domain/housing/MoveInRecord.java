package com.reslife.api.domain.housing;

import com.reslife.api.common.BaseEntity;
import com.reslife.api.domain.resident.Resident;
import com.reslife.api.domain.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "move_in_records")
public class MoveInRecord extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "resident_id", nullable = false)
    private Resident resident;

    @Column(name = "room_number", nullable = false, length = 20)
    private String roomNumber;

    @Column(name = "building_name", nullable = false, length = 100)
    private String buildingName;

    @Column(name = "move_in_date", nullable = false)
    private LocalDate moveInDate;

    @Column(name = "move_out_date")
    private LocalDate moveOutDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "check_in_status", nullable = false, length = 50)
    private CheckInStatus checkInStatus = CheckInStatus.PENDING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recorded_by")
    private User recordedBy;

    @Column(columnDefinition = "TEXT")
    private String notes;
}
