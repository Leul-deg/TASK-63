package com.reslife.api.domain.crawler;

import com.reslife.api.common.BaseEntity;
import com.reslife.api.domain.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * One execution run of a {@link CrawlSource}.
 *
 * <p>A job is created whenever a source is triggered (manually or by schedule).
 * Its {@link #checkpoint} field holds a JSON snapshot of the pending URL queue,
 * enabling the job to be paused and resumed from where it left off.
 *
 * <p>Counter fields ({@link #pagesCrawled}, etc.) are updated atomically by
 * {@code @Modifying} native queries in {@link CrawlJobRepository} to avoid
 * stale-state races between concurrent counter increments.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "crawl_jobs")
public class CrawlJob extends BaseEntity {

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "source_id", nullable = false)
    private CrawlSource source;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false, length = 20)
    private TriggerType triggerType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CrawlStatus status = CrawlStatus.PENDING;

    @Column(name = "pages_crawled", nullable = false)
    private int pagesCrawled = 0;

    @Column(name = "pages_skipped", nullable = false)
    private int pagesSkipped = 0;

    @Column(name = "pages_failed", nullable = false)
    private int pagesFailed = 0;

    @Column(name = "items_found", nullable = false)
    private int itemsFound = 0;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "paused_at")
    private Instant pausedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    /** JSON-serialized {@link CrawlCheckpoint} — null until first checkpoint is saved. */
    @Column(columnDefinition = "TEXT")
    private String checkpoint;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "triggered_by_user_id")
    private User triggeredBy;
}
