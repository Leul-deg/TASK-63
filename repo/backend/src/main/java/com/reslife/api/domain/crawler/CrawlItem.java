package com.reslife.api.domain.crawler;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * An extracted data item from a crawled page.  Rows are append-only.
 *
 * <p>{@link #dataHash} (SHA-256 of the normalized JSON representation) enables
 * cross-job deduplication: if the same hash was seen before for this source,
 * {@link #isNew} is set to {@code false} (item was updated, not created).
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "crawl_items")
@EntityListeners(AuditingEntityListener.class)
public class CrawlItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_id", nullable = false)
    private CrawlSource source;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_id", nullable = false)
    private CrawlJob job;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "page_id")
    private CrawlPage page;

    @Column(length = 2000)
    private String url;

    /** SHA-256 of the canonical JSON form of this item. */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "data_hash", nullable = false, length = 64, columnDefinition = "CHAR(64)")
    private String dataHash;

    @Column(name = "raw_data", nullable = false, columnDefinition = "TEXT")
    private String rawData;

    /** {@code true} if this is the first time this data hash has been seen for this source. */
    @Column(name = "is_new", nullable = false)
    private boolean isNew = true;

    @CreatedDate
    @Column(name = "extracted_at", updatable = false, nullable = false)
    private Instant extractedAt;
}
