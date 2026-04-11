package com.reslife.api.domain.integration;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface IntegrationAuditLogRepository extends JpaRepository<IntegrationAuditLog, UUID> {

    Page<IntegrationAuditLog> findByIntegrationKeyIdOrderByCreatedAtDesc(UUID integrationKeyId, Pageable pageable);

    Page<IntegrationAuditLog> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
