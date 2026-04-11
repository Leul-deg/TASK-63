package com.reslife.api.domain.analytics;

import com.reslife.api.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Cached output of one analytics metric.
 *
 * <p>There is exactly one row per {@link #metricName}.  The row is created on
 * the first compute pass and updated in-place on every subsequent refresh.
 *
 * <p>{@link #value} is stored as JSONB so PostgreSQL can index into it if
 * needed later, but the application always reads it back as a raw JSON string.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "analytics_snapshots")
public class AnalyticsSnapshot extends BaseEntity {

    @Column(name = "metric_name", nullable = false, unique = true, length = 100)
    private String metricName;

    /** JSON-serialized metric payload — structure varies per metric. */
    @Column(columnDefinition = "TEXT", nullable = false)
    private String value;

    @Column(name = "computed_at", nullable = false)
    private Instant computedAt;
}
