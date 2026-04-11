package com.reslife.api.domain.user;

import com.reslife.api.common.SoftDeletableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "users")
@SQLRestriction("deleted_at IS NULL")
public class User extends SoftDeletableEntity {

    @Column(nullable = false, unique = true, length = 100)
    private String username;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "first_name", length = 100)
    private String firstName;

    @Column(name = "last_name", length = 100)
    private String lastName;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_status", nullable = false, length = 20)
    private AccountStatus accountStatus = AccountStatus.ACTIVE;

    @Column(name = "status_reason", columnDefinition = "TEXT")
    private String statusReason;

    /** UUID of the admin user who last changed the account status. Stored loose (not FK) to survive admin deletion. */
    @Column(name = "status_changed_by")
    private UUID statusChangedBy;

    @Column(name = "status_changed_at")
    private Instant statusChangedAt;

    /**
     * Set only by the explicit account-deletion workflow.
     * A scheduled task hard-deletes the row 30 days after this timestamp.
     */
    @Column(name = "scheduled_purge_at")
    private Instant scheduledPurgeAt;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private Set<UserRole> userRoles = new HashSet<>();

    public boolean isActive() {
        return accountStatus == AccountStatus.ACTIVE;
    }

    public String getFullName() {
        return firstName + " " + lastName;
    }
}
