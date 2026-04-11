package com.reslife.api.domain.crawler;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

public interface CrawlSourceRepository extends JpaRepository<CrawlSource, UUID> {

    /** Returns all active, non-deleted sources — used by the scheduler on startup. */
    List<CrawlSource> findByActiveTrueOrderByNameAsc();

    Page<CrawlSource> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @Modifying @Transactional
    @Query(value = "UPDATE crawl_sources SET last_crawled_at = now(), updated_at = now() WHERE id = :id",
           nativeQuery = true)
    void updateLastCrawledAt(@Param("id") UUID id);
}
