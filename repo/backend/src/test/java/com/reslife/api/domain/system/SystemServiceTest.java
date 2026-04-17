package com.reslife.api.domain.system;

import com.reslife.api.domain.user.User;
import com.reslife.api.domain.user.UserService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SystemServiceTest {

    private final ConfigurationVersionRepository configRepo =
            mock(ConfigurationVersionRepository.class);
    private final AuditLogRepository auditRepo = mock(AuditLogRepository.class);
    private final UserService userService = mock(UserService.class);

    private SystemService service;

    private static final UUID USER_ID   = UUID.randomUUID();
    private static final UUID ENTITY_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new SystemService(configRepo, auditRepo, userService);
        when(auditRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(configRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    // ── getConfigValue ────────────────────────────────────────────────────────

    @Test
    void getConfigValue_returnsValue_whenActiveVersionFound() {
        ConfigurationVersion cv = new ConfigurationVersion();
        cv.setValue("some-value");
        when(configRepo.findByKeyAndActiveTrue("feature.flag")).thenReturn(Optional.of(cv));

        assertThat(service.getConfigValue("feature.flag")).isEqualTo("some-value");
    }

    @Test
    void getConfigValue_throwsEntityNotFoundException_whenKeyNotFound() {
        when(configRepo.findByKeyAndActiveTrue("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getConfigValue("missing"))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("missing");
    }

    @Test
    void getConfigValue_withDefault_returnsDefault_whenKeyNotFound() {
        when(configRepo.findByKeyAndActiveTrue("key")).thenReturn(Optional.empty());

        assertThat(service.getConfigValue("key", "fallback")).isEqualTo("fallback");
    }

    @Test
    void getConfigValue_withDefault_returnsStoredValue_whenKeyFound() {
        ConfigurationVersion cv = new ConfigurationVersion();
        cv.setValue("stored");
        when(configRepo.findByKeyAndActiveTrue("key")).thenReturn(Optional.of(cv));

        assertThat(service.getConfigValue("key", "fallback")).isEqualTo("stored");
    }

    // ── setConfigValue ────────────────────────────────────────────────────────

    @Test
    void setConfigValue_deactivatesExistingAndCreatesNewVersion() {
        ConfigurationVersion existing = new ConfigurationVersion();
        existing.setActive(true);
        existing.setVersion(2);
        when(configRepo.findByKeyAndActiveTrue("key")).thenReturn(Optional.of(existing));
        when(configRepo.findTopByKeyOrderByVersionDesc("key")).thenReturn(Optional.of(existing));

        ConfigurationVersion result = service.setConfigValue("key", "newVal", "desc", null);

        assertThat(existing.isActive()).isFalse();
        assertThat(result.getValue()).isEqualTo("newVal");
        assertThat(result.getVersion()).isEqualTo(3);
        assertThat(result.isActive()).isTrue();
    }

    @Test
    void setConfigValue_startsAtVersionOne_whenNoHistory() {
        when(configRepo.findByKeyAndActiveTrue("new.key")).thenReturn(Optional.empty());
        when(configRepo.findTopByKeyOrderByVersionDesc("new.key")).thenReturn(Optional.empty());

        ConfigurationVersion result = service.setConfigValue("new.key", "v", null, null);

        assertThat(result.getVersion()).isEqualTo(1);
    }

    @Test
    void setConfigValue_linksCreator_whenUserIdProvided() {
        when(configRepo.findByKeyAndActiveTrue("k")).thenReturn(Optional.empty());
        when(configRepo.findTopByKeyOrderByVersionDesc("k")).thenReturn(Optional.empty());
        User creator = new User();
        when(userService.findById(USER_ID)).thenReturn(creator);

        ConfigurationVersion result = service.setConfigValue("k", "v", null, USER_ID);

        assertThat(result.getCreatedBy()).isSameAs(creator);
        verify(userService).findById(USER_ID);
    }

    // ── log ───────────────────────────────────────────────────────────────────

    @Test
    void log_createsAuditEntry_withoutUser() {
        AuditLog entry = service.log(
                "ENTITY_CREATED", "Resident", ENTITY_ID, null, "{}", null, "10.0.0.1", "Mozilla/5");

        assertThat(entry.getAction()).isEqualTo("ENTITY_CREATED");
        assertThat(entry.getEntityType()).isEqualTo("Resident");
        assertThat(entry.getEntityId()).isEqualTo(ENTITY_ID);
        assertThat(entry.getIpAddress()).isEqualTo("10.0.0.1");
        assertThat(entry.getUser()).isNull();
        verify(userService, never()).findById(any());
    }

    @Test
    void log_loadsAndLinksUser_whenUserIdProvided() {
        User user = new User();
        when(userService.findById(USER_ID)).thenReturn(user);

        AuditLog entry = service.log(
                "ACTION", "Entity", ENTITY_ID, null, null, USER_ID, null, null);

        assertThat(entry.getUser()).isSameAs(user);
        verify(userService).findById(USER_ID);
    }

    // ── findLogsInRange ───────────────────────────────────────────────────────

    @Test
    void findLogsInRange_delegatesToRepository() {
        Instant from = Instant.parse("2026-01-01T00:00:00Z");
        Instant to   = Instant.parse("2026-01-31T23:59:59Z");
        when(auditRepo.findByCreatedAtBetweenOrderByCreatedAtDesc(from, to))
                .thenReturn(List.of(new AuditLog()));

        List<AuditLog> result = service.findLogsInRange(from, to);

        assertThat(result).hasSize(1);
        verify(auditRepo).findByCreatedAtBetweenOrderByCreatedAtDesc(from, to);
    }
}
