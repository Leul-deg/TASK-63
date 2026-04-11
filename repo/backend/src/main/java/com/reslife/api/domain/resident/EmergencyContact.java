package com.reslife.api.domain.resident;

import com.reslife.api.common.BaseEntity;
import com.reslife.api.encryption.StringEncryptionConverter;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Emergency contact for a resident.
 *
 * <p>{@code name}, {@code relationship}, {@code phone}, and {@code email} are
 * PII and are stored encrypted with AES-256-GCM via
 * {@link StringEncryptionConverter}. The DB columns are TEXT to accommodate
 * the Base64 ciphertext.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "emergency_contacts")
public class EmergencyContact extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "resident_id", nullable = false)
    private Resident resident;

    @Convert(converter = StringEncryptionConverter.class)
    @Column(columnDefinition = "TEXT")
    private String name;

    @Convert(converter = StringEncryptionConverter.class)
    @Column(columnDefinition = "TEXT")
    private String relationship;

    @Convert(converter = StringEncryptionConverter.class)
    @Column(columnDefinition = "TEXT")
    private String phone;

    @Convert(converter = StringEncryptionConverter.class)
    @Column(columnDefinition = "TEXT")
    private String email;

    @Column(name = "is_primary", nullable = false)
    private boolean primary = false;
}
