package com.reslife.api.domain.housing;

import com.reslife.api.common.BaseEntity;
import com.reslife.api.domain.resident.Resident;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "housing_agreements")
public class HousingAgreement extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "resident_id", nullable = false)
    private Resident resident;

    @Column(name = "agreement_type", nullable = false, length = 100)
    private String agreementType;

    @Column(name = "signed_date")
    private LocalDate signedDate;

    @Column(name = "expires_date")
    private LocalDate expiresDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private AgreementStatus status = AgreementStatus.PENDING;

    @Column(name = "document_url", columnDefinition = "TEXT")
    private String documentUrl;

    @Column(length = 20)
    private String version;

    @Column(columnDefinition = "TEXT")
    private String notes;
}
