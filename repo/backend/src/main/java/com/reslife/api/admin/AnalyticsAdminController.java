package com.reslife.api.admin;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reslife.api.domain.analytics.AnalyticsComputeService;
import com.reslife.api.domain.analytics.AnalyticsSnapshot;
import com.reslife.api.domain.analytics.AnalyticsSnapshotRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Admin-only REST endpoints for the analytics dashboard.
 *
 * <pre>
 * GET  /api/admin/analytics         — all four metrics (pre-computed)
 * POST /api/admin/analytics/refresh — trigger an immediate recompute
 * </pre>
 *
 * <p>The GET endpoint is intentionally cheap: it reads four rows from
 * {@code analytics_snapshots} and returns them.  Heavy computation is done
 * by the scheduled {@link AnalyticsComputeService} in the background.
 */
@RestController
@RequestMapping("/api/admin/analytics")
public class AnalyticsAdminController {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsAdminController.class);

    private static final List<String> METRIC_NAMES = List.of(
            "booking_conversion",
            "no_show_rate",
            "slot_utilization",
            "settlement_completion"
    );

    private final AnalyticsSnapshotRepository snapshotRepo;
    private final AnalyticsComputeService     computeService;
    private final ObjectMapper                objectMapper;

    public AnalyticsAdminController(AnalyticsSnapshotRepository snapshotRepo,
                                     AnalyticsComputeService computeService,
                                     ObjectMapper objectMapper) {
        this.snapshotRepo   = snapshotRepo;
        this.computeService = computeService;
        this.objectMapper   = objectMapper;
    }

    /**
     * Returns all four analytics metrics as a single JSON object.
     *
     * <p>Each key is the snake_case metric name; each value contains:
     * <ul>
     *   <li>{@code data} — the metric payload (structure depends on metric)</li>
     *   <li>{@code computedAt} — ISO-8601 timestamp of last compute</li>
     * </ul>
     */
    @GetMapping
    public Map<String, Object> dashboard() {
        Map<String, Object> result = new LinkedHashMap<>();
        for (AnalyticsSnapshot snap : snapshotRepo.findAll()) {
            try {
                Map<String, Object> parsed = objectMapper.readValue(
                        snap.getValue(), new TypeReference<>() {});
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("data",        parsed);
                entry.put("computedAt",  snap.getComputedAt());
                result.put(snap.getMetricName(), entry);
            } catch (Exception e) {
                log.warn("Could not deserialise snapshot '{}': {}", snap.getMetricName(), e.getMessage());
                result.put(snap.getMetricName(), Map.of("error", "Parse error"));
            }
        }
        return result;
    }

    /**
     * Forces an immediate recompute of all metrics.
     * Useful after bulk data imports or for testing.
     */
    @PostMapping("/refresh")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void refresh() {
        computeService.refresh();
    }
}
