package com.reslife.api.domain.analytics;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

/**
 * Computes and caches the four core analytics metrics.
 *
 * <p>All four metrics are computed on startup ({@link #refreshOnStartup}) and
 * then automatically every 15 minutes ({@link #scheduledRefresh}).  Each result
 * is serialised to JSON and stored in {@code analytics_snapshots} as a single
 * row keyed by metric name — this lets the read path return pre-computed data
 * without hitting multiple large tables on every dashboard request.
 *
 * <h3>Metrics</h3>
 * <ul>
 *   <li><b>booking_conversion</b> — confirmed or completed bookings / total bookings × 100.</li>
 *   <li><b>no_show_rate</b> — NO_SHOW / all decided move-in records × 100.</li>
 *   <li><b>slot_utilization</b> — currently CHECKED_IN rooms / total known rooms, by building.</li>
 *   <li><b>settlement_completion</b> — acknowledged / total acknowledgment-required
 *       SETTLEMENT + ARBITRATION notifications × 100.</li>
 * </ul>
 */
@Service
public class AnalyticsComputeService {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsComputeService.class);

    private static final String BOOKING_CONVERSION     = "booking_conversion";
    private static final String NO_SHOW_RATE          = "no_show_rate";
    private static final String SLOT_UTILIZATION      = "slot_utilization";
    private static final String SETTLEMENT_COMPLETION = "settlement_completion";

    private final JdbcTemplate                 jdbc;
    private final AnalyticsSnapshotRepository  snapshotRepo;
    private final ObjectMapper                 objectMapper;

    public AnalyticsComputeService(JdbcTemplate jdbc,
                                    AnalyticsSnapshotRepository snapshotRepo,
                                    ObjectMapper objectMapper) {
        this.jdbc         = jdbc;
        this.snapshotRepo = snapshotRepo;
        this.objectMapper = objectMapper;
    }

    // ── Lifecycle ──────────────────────────────────────────────────────────

    @PostConstruct
    public void refreshOnStartup() {
        refresh();
    }

    @Scheduled(fixedDelay = 900_000) // 15 minutes after last completion
    public void scheduledRefresh() {
        refresh();
    }

    /** Computes all four metrics and persists them. */
    public void refresh() {
        long start = System.currentTimeMillis();
        try {
            saveSnapshot(BOOKING_CONVERSION,     computeBookingConversion());
            saveSnapshot(NO_SHOW_RATE,          computeNoShowRate());
            saveSnapshot(SLOT_UTILIZATION,      computeSlotUtilization());
            saveSnapshot(SETTLEMENT_COMPLETION, computeSettlementCompletion());
            log.info("Analytics refresh complete in {}ms", System.currentTimeMillis() - start);
        } catch (Exception e) {
            log.error("Analytics refresh failed: {}", e.getMessage(), e);
        }
    }

    // ── Metric computations ────────────────────────────────────────────────

    private Map<String, Object> computeBookingConversion() {
        Map<String, Long> byStatus = new LinkedHashMap<>();
        jdbc.queryForList("SELECT status, COUNT(*) AS cnt FROM resident_bookings GROUP BY status ORDER BY status")
            .forEach(r -> byStatus.put((String) r.get("status"), toLong(r.get("cnt"))));

        long requested = byStatus.getOrDefault("REQUESTED", 0L);
        long confirmed = byStatus.getOrDefault("CONFIRMED", 0L);
        long completed = byStatus.getOrDefault("COMPLETED", 0L);
        long cancelled = byStatus.getOrDefault("CANCELLED", 0L);
        long noShow    = byStatus.getOrDefault("NO_SHOW", 0L);
        long total     = requested + confirmed + completed + cancelled + noShow;
        long converted = confirmed + completed;
        double rate    = total > 0 ? Math.round((converted * 1000.0 / total)) / 10.0 : 0.0;

        // Monthly conversions over last 6 months
        List<Map<String, Object>> monthlyTrend = jdbc.queryForList(
            "SELECT TO_CHAR(DATE_TRUNC('month', created_at), 'YYYY-MM') AS month, " +
            "       COUNT(*) FILTER (WHERE status IN ('CONFIRMED', 'COMPLETED')) AS converted, " +
            "       COUNT(*) AS total " +
            "FROM   resident_bookings " +
            "WHERE  created_at >= NOW() - INTERVAL '6 months' " +
            "GROUP  BY month ORDER BY month"
        );

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("byStatus",        byStatus);
        result.put("requested",       requested);
        result.put("confirmed",       confirmed);
        result.put("completed",       completed);
        result.put("cancelled",       cancelled);
        result.put("noShow",          noShow);
        result.put("conversionRate",  rate);
        result.put("monthlyTrend",    monthlyTrend);
        return result;
    }

    private Map<String, Object> computeNoShowRate() {
        Map<String, Long> byStatus = new LinkedHashMap<>();
        jdbc.queryForList("SELECT check_in_status, COUNT(*) AS cnt FROM move_in_records GROUP BY check_in_status ORDER BY check_in_status")
            .forEach(r -> byStatus.put((String) r.get("check_in_status"), toLong(r.get("cnt"))));

        long noShow    = byStatus.getOrDefault("NO_SHOW",     0L);
        long checkedIn = byStatus.getOrDefault("CHECKED_IN",  0L);
        long checkedOut= byStatus.getOrDefault("CHECKED_OUT", 0L);
        long cancelled = byStatus.getOrDefault("CANCELLED",   0L);
        long decided   = noShow + checkedIn + checkedOut + cancelled; // excludes PENDING
        double rate    = decided > 0 ? Math.round((noShow * 1000.0 / decided)) / 10.0 : 0.0;

        // Monthly no-show count and totals over last 6 months
        List<Map<String, Object>> monthlyTrend = jdbc.queryForList(
            "SELECT TO_CHAR(DATE_TRUNC('month', created_at), 'YYYY-MM') AS month, " +
            "       COUNT(*) FILTER (WHERE check_in_status = 'NO_SHOW')  AS no_shows, " +
            "       COUNT(*) FILTER (WHERE check_in_status != 'PENDING') AS decided " +
            "FROM   move_in_records " +
            "WHERE  created_at >= NOW() - INTERVAL '6 months' " +
            "GROUP  BY month ORDER BY month"
        );

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("byStatus",     byStatus);
        result.put("noShow",       noShow);
        result.put("checkedIn",    checkedIn);
        result.put("checkedOut",   checkedOut);
        result.put("cancelled",    cancelled);
        result.put("noShowRate",   rate);
        result.put("monthlyTrend", monthlyTrend);
        return result;
    }

    private Map<String, Object> computeSlotUtilization() {
        // Per-building: occupied (CHECKED_IN with no past move-out) and total known rooms
        List<Map<String, Object>> perBuilding = jdbc.queryForList(
            "SELECT building_name, " +
            "       COUNT(DISTINCT room_number) FILTER (" +
            "           WHERE check_in_status = 'CHECKED_IN' " +
            "             AND (move_out_date IS NULL OR move_out_date >= CURRENT_DATE)" +
            "       ) AS occupied, " +
            "       COUNT(DISTINCT room_number) AS total " +
            "FROM   move_in_records " +
            "GROUP  BY building_name ORDER BY building_name"
        );

        long totalOccupied = 0, totalSlots = 0;
        List<Map<String, Object>> byBuilding = new ArrayList<>();
        for (Map<String, Object> row : perBuilding) {
            long occ   = toLong(row.get("occupied"));
            long total = toLong(row.get("total"));
            totalOccupied += occ;
            totalSlots    += total;
            double bRate   = total > 0 ? Math.round((occ * 1000.0 / total)) / 10.0 : 0.0;
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("building",         row.get("building_name"));
            entry.put("occupied",         occ);
            entry.put("total",            total);
            entry.put("utilizationRate",  bRate);
            byBuilding.add(entry);
        }
        double overallRate = totalSlots > 0
                ? Math.round((totalOccupied * 1000.0 / totalSlots)) / 10.0 : 0.0;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("occupiedSlots",    totalOccupied);
        result.put("totalSlots",       totalSlots);
        result.put("utilizationRate",  overallRate);
        result.put("byBuilding",       byBuilding);
        return result;
    }

    private Map<String, Object> computeSettlementCompletion() {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT category, " +
            "       COUNT(*)              AS total, " +
            "       COUNT(acknowledged_at) AS completed " +
            "FROM   notifications " +
            "WHERE  requires_acknowledgment = TRUE " +
            "  AND  category IN ('SETTLEMENT', 'ARBITRATION') " +
            "GROUP  BY category ORDER BY category"
        );

        long totalRequired = 0, totalCompleted = 0;
        Map<String, Map<String, Object>> byCategory = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            long t = toLong(row.get("total"));
            long c = toLong(row.get("completed"));
            totalRequired  += t;
            totalCompleted += c;
            Map<String, Object> cat = new LinkedHashMap<>();
            cat.put("total",     t);
            cat.put("completed", c);
            cat.put("pending",   t - c);
            byCategory.put((String) row.get("category"), cat);
        }
        double rate = totalRequired > 0
                ? Math.round((totalCompleted * 1000.0 / totalRequired)) / 10.0 : 0.0;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalRequired",   totalRequired);
        result.put("totalCompleted",  totalCompleted);
        result.put("totalPending",    totalRequired - totalCompleted);
        result.put("completionRate",  rate);
        result.put("byCategory",      byCategory);
        return result;
    }

    // ── Persistence ────────────────────────────────────────────────────────

    private void saveSnapshot(String metricName, Map<String, Object> data) {
        try {
            String json = objectMapper.writeValueAsString(data);
            AnalyticsSnapshot snap = snapshotRepo.findByMetricName(metricName)
                .orElseGet(AnalyticsSnapshot::new);
            snap.setMetricName(metricName);
            snap.setValue(json);
            snap.setComputedAt(Instant.now());
            snapshotRepo.save(snap);
        } catch (Exception e) {
            log.warn("Could not save snapshot for '{}': {}", metricName, e.getMessage());
        }
    }

    // ── Utilities ──────────────────────────────────────────────────────────

    private static long toLong(Object value) {
        if (value == null) return 0L;
        return ((Number) value).longValue();
    }
}
