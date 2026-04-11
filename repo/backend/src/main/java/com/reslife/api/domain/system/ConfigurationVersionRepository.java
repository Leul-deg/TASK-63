package com.reslife.api.domain.system;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConfigurationVersionRepository extends JpaRepository<ConfigurationVersion, UUID> {
    Optional<ConfigurationVersion> findByKeyAndActiveTrue(String key);
    List<ConfigurationVersion> findByKeyOrderByVersionDesc(String key);
    Optional<ConfigurationVersion> findTopByKeyOrderByVersionDesc(String key);
    boolean existsByKeyAndActiveTrue(String key);
    Optional<ConfigurationVersion> findByKeyAndVersion(String key, int version);
}
