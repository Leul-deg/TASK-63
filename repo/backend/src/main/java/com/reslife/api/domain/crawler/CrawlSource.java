package com.reslife.api.domain.crawler;

import com.reslife.api.common.SoftDeletableEntity;
import com.reslife.api.domain.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A configured data source for the collection engine.
 *
 * <p>Defines where to crawl (base URL), how to crawl (config JSON),
 * when to crawl (cron/interval), and throttle limits.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "crawl_sources")
@SQLRestriction("deleted_at IS NULL")
public class CrawlSource extends SoftDeletableEntity {

    @Column(nullable = false, length = 255)
    private String name;

    @Column(name = "base_url", nullable = false, length = 1000)
    private String baseUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "site_type", nullable = false, length = 20)
    private SiteType siteType = SiteType.HTML;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 100)
    private String city;

    /** JSON array of keyword strings for content filtering. */
    @Column(columnDefinition = "TEXT")
    private String keywords;

    /**
     * JSON-serialized {@link CrawlConfig}.
     * Default {@code "{}"} produces all-default config.
     */
    @Column(name = "crawl_config", nullable = false, columnDefinition = "TEXT")
    private String crawlConfig = "{}";

    /** Spring cron expression, for example every 6 hours. */
    @Column(name = "schedule_cron", length = 100)
    private String scheduleCron;

    /** Seconds between runs, as an alternative to a cron expression. */
    @Column(name = "schedule_interval_seconds")
    private Long scheduleIntervalSeconds;

    @Column(name = "requests_per_second", nullable = false, precision = 6, scale = 2)
    private BigDecimal requestsPerSecond = BigDecimal.valueOf(1.0);

    @Column(name = "delay_ms_between_requests", nullable = false)
    private int delayMsBetweenRequests = 1_000;

    @Column(name = "max_depth", nullable = false)
    private int maxDepth = 3;

    @Column(name = "max_pages", nullable = false)
    private int maxPages = 100;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "last_crawled_at")
    private Instant lastCrawledAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id")
    private User createdBy;

    /** Returns {@code true} if a schedule (cron or interval) is configured. */
    public boolean hasSchedule() {
        return scheduleCron != null || scheduleIntervalSeconds != null;
    }
}
