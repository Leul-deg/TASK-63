package com.reslife.api.domain.crawler;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface CrawlPageRepository extends JpaRepository<CrawlPage, UUID> {

    Page<CrawlPage> findByJobIdOrderByCreatedAtDesc(UUID jobId, Pageable pageable);

    long countByJobIdAndStatus(UUID jobId, PageStatus status);

    /**
     * Returns the URL hashes of all pages in this job that have already reached a
     * terminal state (FETCHED, SKIPPED, ERROR).  Used to reconstruct the visited
     * set when resuming a paused job, avoiding re-fetching completed pages.
     */
    @Query("SELECT p.urlHash FROM CrawlPage p " +
           "WHERE p.job.id = :jobId AND p.status IN ('FETCHED','SKIPPED','ERROR')")
    Set<String> findVisitedUrlHashesByJobId(@Param("jobId") UUID jobId);

    /**
     * Finds the most recent content hash for a URL across all completed jobs for a
     * given source.  Used by the incremental-update check: if the new hash matches
     * the last known hash, the page content hasn't changed.
     */
    @Query("SELECT p.contentHash FROM CrawlPage p " +
           "WHERE p.job.source.id = :sourceId AND p.url = :url " +
           "AND p.status = com.reslife.api.domain.crawler.PageStatus.FETCHED " +
           "ORDER BY p.fetchedAt DESC")
    List<String> findRecentContentHashesForUrl(
            @Param("sourceId") UUID sourceId,
            @Param("url") String url,
            Pageable pageable);

    /** Atomic page status update with timestamp — avoids a load+save round trip. */
    @Modifying @Transactional
    @Query(value = "UPDATE crawl_pages SET status = :status, http_status = :httpStatus, " +
                   "content_hash = :contentHash, content_length = :contentLength, " +
                   "fetched_at = now(), updated_at = now(), error_message = :errorMsg " +
                   "WHERE id = :id",
           nativeQuery = true)
    void updateFetchResult(@Param("id") UUID id,
                           @Param("status") String status,
                           @Param("httpStatus") Integer httpStatus,
                           @Param("contentHash") String contentHash,
                           @Param("contentLength") Integer contentLength,
                           @Param("errorMsg") String errorMsg);
}
