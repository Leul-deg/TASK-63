package com.reslife.api.domain.analytics;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link AnalyticsComputeService}.
 *
 * <p>JdbcTemplate and the repository are mocked so the tests exercise
 * the pure computation logic (percentage math, upsert behaviour) without
 * needing a running database.
 */
class AnalyticsComputeServiceTest {

    private final JdbcTemplate                jdbc        = mock(JdbcTemplate.class);
    private final AnalyticsSnapshotRepository snapshotRepo = mock(AnalyticsSnapshotRepository.class);
    private final ObjectMapper                objectMapper = new ObjectMapper();

    private AnalyticsComputeService service;

    @BeforeEach
    void setUp() {
        service = new AnalyticsComputeService(jdbc, snapshotRepo, objectMapper);

        // Default: all SQL queries return empty lists (zero-data baseline)
        when(jdbc.queryForList(anyString())).thenReturn(Collections.emptyList());
        // Default: no pre-existing snapshot — each metric creates a new row
        when(snapshotRepo.findByMetricName(anyString())).thenReturn(Optional.empty());
        when(snapshotRepo.save(any(AnalyticsSnapshot.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    // ── Completeness ─────────────────────────────────────────────────────────

    @Test
    void refresh_savesAllFourMetrics_whenDatabaseIsEmpty() {
        service.refresh();

        ArgumentCaptor<AnalyticsSnapshot> captor = ArgumentCaptor.forClass(AnalyticsSnapshot.class);
        verify(snapshotRepo, times(4)).save(captor.capture());

        Set<String> savedNames = captor.getAllValues().stream()
                .map(AnalyticsSnapshot::getMetricName)
                .collect(Collectors.toSet());
        assertThat(savedNames).containsExactlyInAnyOrder(
                "booking_conversion", "no_show_rate", "slot_utilization", "settlement_completion");
    }

    @Test
    void refresh_setsConversionRateToZero_whenNoBookings() {
        service.refresh();

        ArgumentCaptor<AnalyticsSnapshot> captor = ArgumentCaptor.forClass(AnalyticsSnapshot.class);
        verify(snapshotRepo, times(4)).save(captor.capture());

        AnalyticsSnapshot snap = captor.getAllValues().stream()
                .filter(s -> "booking_conversion".equals(s.getMetricName()))
                .findFirst().orElseThrow();
        assertThat(snap.getValue()).contains("\"conversionRate\":0.0");
    }

    // ── Booking conversion rate ───────────────────────────────────────────────

    @Test
    void refresh_computesBookingConversionRate_for50Percent() {
        // 60 CONFIRMED + 40 COMPLETED out of 200 total → 50.0 %
        when(jdbc.queryForList(argThat(s -> s.contains("GROUP BY status"))))
                .thenReturn(List.of(
                        Map.of("status", "CONFIRMED", "cnt", 60L),
                        Map.of("status", "COMPLETED", "cnt", 40L),
                        Map.of("status", "REQUESTED", "cnt", 100L)));

        service.refresh();

        ArgumentCaptor<AnalyticsSnapshot> captor = ArgumentCaptor.forClass(AnalyticsSnapshot.class);
        verify(snapshotRepo, times(4)).save(captor.capture());

        AnalyticsSnapshot snap = captor.getAllValues().stream()
                .filter(s -> "booking_conversion".equals(s.getMetricName()))
                .findFirst().orElseThrow();
        assertThat(snap.getValue()).contains("\"conversionRate\":50.0");
    }

    // ── No-show rate ──────────────────────────────────────────────────────────

    @Test
    void refresh_computesNoShowRate_for10Percent() {
        // 10 NO_SHOW out of 100 decided → 10.0 %
        when(jdbc.queryForList(argThat(s -> s.contains("GROUP BY check_in_status"))))
                .thenReturn(List.of(
                        Map.of("check_in_status", "NO_SHOW",    "cnt", 10L),
                        Map.of("check_in_status", "CHECKED_IN", "cnt", 90L)));

        service.refresh();

        ArgumentCaptor<AnalyticsSnapshot> captor = ArgumentCaptor.forClass(AnalyticsSnapshot.class);
        verify(snapshotRepo, times(4)).save(captor.capture());

        AnalyticsSnapshot snap = captor.getAllValues().stream()
                .filter(s -> "no_show_rate".equals(s.getMetricName()))
                .findFirst().orElseThrow();
        assertThat(snap.getValue()).contains("\"noShowRate\":10.0");
    }

    // ── Slot utilization ─────────────────────────────────────────────────────

    @Test
    void refresh_computesSlotUtilizationRate_forKnownBuilding() {
        // West Hall: 40 occupied out of 50 total → 80.0 %
        when(jdbc.queryForList(argThat(s -> s.contains("building_name"))))
                .thenReturn(List.of(
                        Map.of("building_name", "West Hall", "occupied", 40L, "total", 50L)));

        service.refresh();

        ArgumentCaptor<AnalyticsSnapshot> captor = ArgumentCaptor.forClass(AnalyticsSnapshot.class);
        verify(snapshotRepo, times(4)).save(captor.capture());

        AnalyticsSnapshot snap = captor.getAllValues().stream()
                .filter(s -> "slot_utilization".equals(s.getMetricName()))
                .findFirst().orElseThrow();
        assertThat(snap.getValue()).contains("\"utilizationRate\":80.0");
        assertThat(snap.getValue()).contains("West Hall");
    }

    // ── Settlement completion ─────────────────────────────────────────────────

    @Test
    void refresh_computesSettlementCompletionRate_for80Percent() {
        // 8 of 10 SETTLEMENT notifications acknowledged → 80.0 %
        when(jdbc.queryForList(argThat(s -> s.contains("SETTLEMENT"))))
                .thenReturn(List.of(
                        Map.of("category", "SETTLEMENT", "total", 10L, "completed", 8L)));

        service.refresh();

        ArgumentCaptor<AnalyticsSnapshot> captor = ArgumentCaptor.forClass(AnalyticsSnapshot.class);
        verify(snapshotRepo, times(4)).save(captor.capture());

        AnalyticsSnapshot snap = captor.getAllValues().stream()
                .filter(s -> "settlement_completion".equals(s.getMetricName()))
                .findFirst().orElseThrow();
        assertThat(snap.getValue()).contains("\"completionRate\":80.0");
    }

    // ── Upsert behaviour ─────────────────────────────────────────────────────

    @Test
    void refresh_updatesExistingSnapshot_ratherThanCreatingNewOne() {
        AnalyticsSnapshot existing = new AnalyticsSnapshot();
        existing.setMetricName("booking_conversion");
        existing.setValue("{}");
        when(snapshotRepo.findByMetricName("booking_conversion")).thenReturn(Optional.of(existing));

        service.refresh();

        ArgumentCaptor<AnalyticsSnapshot> captor = ArgumentCaptor.forClass(AnalyticsSnapshot.class);
        verify(snapshotRepo, atLeastOnce()).save(captor.capture());

        AnalyticsSnapshot saved = captor.getAllValues().stream()
                .filter(s -> "booking_conversion".equals(s.getMetricName()))
                .findFirst().orElseThrow();
        // Must reuse the existing instance, not create a new AnalyticsSnapshot
        assertThat(saved).isSameAs(existing);
        // And the value must have been updated with fresh JSON
        assertThat(saved.getValue()).isNotEqualTo("{}");
        assertThat(saved.getComputedAt()).isNotNull();
    }
}
