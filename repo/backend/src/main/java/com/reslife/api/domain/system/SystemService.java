package com.reslife.api.domain.system;

import com.reslife.api.domain.user.User;
import com.reslife.api.domain.user.UserService;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class SystemService {

    private final ConfigurationVersionRepository configRepository;
    private final AuditLogRepository auditLogRepository;
    private final UserService userService;

    public SystemService(ConfigurationVersionRepository configRepository,
                         AuditLogRepository auditLogRepository,
                         UserService userService) {
        this.configRepository = configRepository;
        this.auditLogRepository = auditLogRepository;
        this.userService = userService;
    }

    // --- Configuration ---

    public String getConfigValue(String key) {
        return configRepository.findByKeyAndActiveTrue(key)
                .map(ConfigurationVersion::getValue)
                .orElseThrow(() -> new EntityNotFoundException("Configuration not found for key: " + key));
    }

    public String getConfigValue(String key, String defaultValue) {
        return configRepository.findByKeyAndActiveTrue(key)
                .map(ConfigurationVersion::getValue)
                .orElse(defaultValue);
    }

    @Transactional
    public ConfigurationVersion setConfigValue(String key, String value, String description, UUID createdByUserId) {
        // Deactivate current active version
        configRepository.findByKeyAndActiveTrue(key).ifPresent(current -> {
            current.setActive(false);
            configRepository.save(current);
        });

        int nextVersion = configRepository.findTopByKeyOrderByVersionDesc(key)
                .map(cv -> cv.getVersion() + 1)
                .orElse(1);

        ConfigurationVersion newVersion = new ConfigurationVersion();
        newVersion.setKey(key);
        newVersion.setValue(value);
        newVersion.setDescription(description);
        newVersion.setVersion(nextVersion);
        newVersion.setActive(true);

        if (createdByUserId != null) {
            User creator = userService.findById(createdByUserId);
            newVersion.setCreatedBy(creator);
        }

        return configRepository.save(newVersion);
    }

    public List<ConfigurationVersion> getConfigHistory(String key) {
        return configRepository.findByKeyOrderByVersionDesc(key);
    }

    // --- Audit Logs ---

    @Transactional
    public AuditLog log(String action, String entityType, UUID entityId,
                        String oldValues, String newValues,
                        UUID userId, String ipAddress, String userAgent) {
        AuditLog entry = new AuditLog();
        entry.setAction(action);
        entry.setEntityType(entityType);
        entry.setEntityId(entityId);
        entry.setOldValues(oldValues);
        entry.setNewValues(newValues);
        entry.setIpAddress(ipAddress);
        entry.setUserAgent(userAgent);

        if (userId != null) {
            entry.setUser(userService.findById(userId));
        }

        return auditLogRepository.save(entry);
    }

    public Page<AuditLog> findLogsForEntity(String entityType, UUID entityId, Pageable pageable) {
        return auditLogRepository.findByEntityTypeAndEntityIdOrderByCreatedAtDesc(entityType, entityId, pageable);
    }

    public Page<AuditLog> findLogsForUser(UUID userId, Pageable pageable) {
        return auditLogRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
    }

    public List<AuditLog> findLogsInRange(Instant from, Instant to) {
        return auditLogRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(from, to);
    }
}
