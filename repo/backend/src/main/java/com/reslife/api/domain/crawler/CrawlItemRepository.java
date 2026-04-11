package com.reslife.api.domain.crawler;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CrawlItemRepository extends JpaRepository<CrawlItem, UUID> {

    Page<CrawlItem> findByJobIdOrderByExtractedAtDesc(UUID jobId, Pageable pageable);

    Page<CrawlItem> findBySourceIdOrderByExtractedAtDesc(UUID sourceId, Pageable pageable);

    /** Used for deduplication: check if this exact data hash was previously saved for this source. */
    Optional<CrawlItem> findFirstBySourceIdAndDataHash(UUID sourceId, String dataHash);

    long countByJobId(UUID jobId);
}
