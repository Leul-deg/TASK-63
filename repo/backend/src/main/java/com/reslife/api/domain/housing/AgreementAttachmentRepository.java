package com.reslife.api.domain.housing;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface AgreementAttachmentRepository extends JpaRepository<AgreementAttachment, UUID> {

    List<AgreementAttachment> findByAgreementIdOrderByCreatedAtAsc(UUID agreementId);

    @Query("SELECT COUNT(a) FROM AgreementAttachment a WHERE a.agreement.id = :agreementId")
    long countByAgreementId(@Param("agreementId") UUID agreementId);
}
