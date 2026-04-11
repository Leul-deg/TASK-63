package com.reslife.api.domain.resident;

import com.reslife.api.common.SoftDeletableEntity;
import com.reslife.api.encryption.LocalDateEncryptionConverter;
import com.reslife.api.domain.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "residents")
@SQLRestriction("deleted_at IS NULL")
public class Resident extends SoftDeletableEntity {

    @Column(name = "student_id", unique = true, length = 50)
    private String studentId;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(length = 30)
    private String phone;

    /**
     * Encrypted at rest with AES-256-GCM.
     * The DB column is TEXT (holds Base64 ciphertext); the Java type remains LocalDate.
     */
    @Convert(converter = LocalDateEncryptionConverter.class)
    @Column(name = "date_of_birth", columnDefinition = "TEXT")
    private LocalDate dateOfBirth;

    @Column(name = "enrollment_status", length = 50)
    private String enrollmentStatus;

    @Column(name = "room_number", length = 20)
    private String roomNumber;

    @Column(name = "building_name", length = 100)
    private String buildingName;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "class_year", columnDefinition = "SMALLINT")
    private Integer classYear;

    @Column(name = "department", length = 100)
    private String department;

    /**
     * Optional stable link to the owning student account.
     * Self-service endpoints resolve through this FK instead of mutable email equality.
     */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", unique = true)
    private User linkedUser;

    @OneToMany(mappedBy = "resident", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<EmergencyContact> emergencyContacts = new ArrayList<>();

    public String getFullName() {
        return firstName + " " + lastName;
    }
}
