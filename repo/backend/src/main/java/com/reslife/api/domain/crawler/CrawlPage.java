package com.reslife.api.domain.crawler;

import com.reslife.api.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * Record of one URL fetched within a {@link CrawlJob}.
 *
 * <p>{@link #urlHash} (SHA-256 of the URL) is stored alongside the raw URL to
 * support efficient deduplication — the unique constraint {@code (job_id, url_hash)}
 * prevents double-processing within a single run.
 *
 * <p>{@link #contentHash} (SHA-256 of the response body) is compared across jobs
 * for the same source to implement incremental updates: if the hash hasn't changed
 * since the last completed crawl the page is marked {@link PageStatus#SKIPPED}.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
    name = "crawl_pages",
    uniqueConstraints = @UniqueConstraint(name = "uq_crawl_pages_job_url", columnNames = {"job_id", "url_hash"})
)
public class CrawlPage extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_id", nullable = false)
    private CrawlJob job;

    @Column(nullable = false, length = 2000)
    private String url;

    /** SHA-256 of the URL string. */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "url_hash", nullable = false, length = 64, columnDefinition = "CHAR(64)")
    private String urlHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PageStatus status = PageStatus.PENDING;

    @Column(name = "http_status")
    private Integer httpStatus;

    /** SHA-256 of the normalized response body — used for incremental update detection. */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "content_hash", length = 64, columnDefinition = "CHAR(64)")
    private String contentHash;

    @Column(name = "content_length")
    private Integer contentLength;

    @Column(nullable = false)
    private int depth = 0;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "fetched_at")
    private Instant fetchedAt;
}
