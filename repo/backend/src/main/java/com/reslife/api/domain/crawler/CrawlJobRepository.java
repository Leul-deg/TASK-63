package com.reslife.api.domain.crawler;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CrawlJobRepository extends JpaRepository<CrawlJob, UUID> {

    Page<CrawlJob> findBySourceIdOrderByCreatedAtDesc(UUID sourceId, Pageable pageable);

    Page<CrawlJob> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<CrawlJob> findByStatusOrderByCreatedAtDesc(CrawlStatus status, Pageable pageable);

    /** Returns any RUNNING or PAUSED job for this source — at most one per source. */
    Optional<CrawlJob> findFirstBySourceIdAndStatusIn(UUID sourceId, List<CrawlStatus> statuses);

    /** Used on startup to recover jobs that were RUNNING when the server last shut down. */
    List<CrawlJob> findByStatus(CrawlStatus status);

    // ── Atomic counter increments ──────────────────────────────────────────
    // Native SQL so we can do an atomic += 1 and also update updated_at,
    // bypassing the load→modify→save pattern that would cause stale-state races.

    @Modifying @Transactional
    @Query(value = "UPDATE crawl_jobs SET pages_crawled = pages_crawled + 1, updated_at = now() WHERE id = :id",
           nativeQuery = true)
    void incrementPagesCrawled(@Param("id") UUID id);

    @Modifying @Transactional
    @Query(value = "UPDATE crawl_jobs SET pages_skipped = pages_skipped + 1, updated_at = now() WHERE id = :id",
           nativeQuery = true)
    void incrementPagesSkipped(@Param("id") UUID id);

    @Modifying @Transactional
    @Query(value = "UPDATE crawl_jobs SET pages_failed = pages_failed + 1, updated_at = now() WHERE id = :id",
           nativeQuery = true)
    void incrementPagesFailed(@Param("id") UUID id);

    @Modifying @Transactional
    @Query(value = "UPDATE crawl_jobs SET items_found = items_found + 1, updated_at = now() WHERE id = :id",
           nativeQuery = true)
    void incrementItemsFound(@Param("id") UUID id);

    @Modifying @Transactional
    @Query(value = "UPDATE crawl_jobs SET checkpoint = :checkpoint, updated_at = now() WHERE id = :id",
           nativeQuery = true)
    void updateCheckpoint(@Param("id") UUID id, @Param("checkpoint") String checkpoint);

    @Modifying @Transactional
    @Query(value = "UPDATE crawl_jobs SET status = :status, updated_at = now() WHERE id = :id",
           nativeQuery = true)
    void updateStatus(@Param("id") UUID id, @Param("status") String status);

}
