package com.reslife.api.domain.integration;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface IntegrationKeyRepository extends JpaRepository<IntegrationKey, UUID> {

    /** Used by the auth filter on every inbound request. */
    Optional<IntegrationKey> findByKeyIdAndActiveTrue(String keyId);

    Optional<IntegrationKey> findByKeyId(String keyId);

    Page<IntegrationKey> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
