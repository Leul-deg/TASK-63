package com.reslife.api.domain.crawler;

import com.reslife.api.encryption.EncryptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JPA-layer tests for {@link CrawlJobRepository}.
 *
 * <p>The native {@code @Modifying} increment queries are of particular interest:
 * they bypass Hibernate's first-level cache and perform an atomic {@code UPDATE … +1},
 * which is only verifiable by reading back from the database after a flush/clear.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:reslife_crawl;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DEFAULT_NULL_ORDERING=HIGH",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "app.encryption.key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
    "spring.session.store-type=none",
    "spring.jpa.show-sql=false"
})
@Import(EncryptionService.class)
class CrawlJobRepositoryTest {

    @Autowired private CrawlJobRepository    jobRepo;
    @Autowired private CrawlSourceRepository sourceRepo;
    @Autowired private TestEntityManager     em;

    private CrawlSource source;

    @BeforeEach
    void setUp() {
        CrawlSource s = new CrawlSource();
        s.setName("Test Source");
        s.setBaseUrl("http://example.com");
        s.setSiteType(SiteType.HTML);
        source = em.persistAndFlush(s);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private CrawlJob job(CrawlStatus status) {
        CrawlJob j = new CrawlJob();
        j.setSource(source);
        j.setTriggerType(TriggerType.MANUAL);
        j.setStatus(status);
        return em.persistAndFlush(j);
    }

    // ── findByStatus ──────────────────────────────────────────────────────────

    @Test
    void findByStatus_returnsMatchingJobs() {
        job(CrawlStatus.RUNNING);
        job(CrawlStatus.PENDING);
        job(CrawlStatus.COMPLETED);

        List<CrawlJob> running = jobRepo.findByStatus(CrawlStatus.RUNNING);
        assertThat(running).hasSize(1);
        assertThat(running.get(0).getStatus()).isEqualTo(CrawlStatus.RUNNING);
    }

    @Test
    void findByStatus_returnsEmpty_whenNoneMatch() {
        job(CrawlStatus.COMPLETED);
        assertThat(jobRepo.findByStatus(CrawlStatus.RUNNING)).isEmpty();
    }

    // ── findFirstBySourceIdAndStatusIn ────────────────────────────────────────

    @Test
    void findFirstBySourceIdAndStatusIn_returnsJob_whenActiveJobExists() {
        CrawlJob active = job(CrawlStatus.RUNNING);

        Optional<CrawlJob> found = jobRepo.findFirstBySourceIdAndStatusIn(
                source.getId(), List.of(CrawlStatus.RUNNING, CrawlStatus.PAUSED));
        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(active.getId());
    }

    @Test
    void findFirstBySourceIdAndStatusIn_returnsEmpty_whenNoActiveJob() {
        job(CrawlStatus.COMPLETED);

        Optional<CrawlJob> found = jobRepo.findFirstBySourceIdAndStatusIn(
                source.getId(), List.of(CrawlStatus.RUNNING, CrawlStatus.PAUSED));
        assertThat(found).isEmpty();
    }

    // ── findBySourceIdOrderByCreatedAtDesc ────────────────────────────────────

    @Test
    void findBySourceId_returnsPageForThatSourceOnly() {
        job(CrawlStatus.COMPLETED);
        job(CrawlStatus.COMPLETED);

        // Different source — its job must not appear
        CrawlSource other = new CrawlSource();
        other.setName("Other Source");
        other.setBaseUrl("http://other.com");
        other.setSiteType(SiteType.HTML);
        CrawlSource savedOther = em.persistAndFlush(other);
        CrawlJob otherJob = new CrawlJob();
        otherJob.setSource(savedOther);
        otherJob.setTriggerType(TriggerType.MANUAL);
        otherJob.setStatus(CrawlStatus.PENDING);
        em.persistAndFlush(otherJob);

        Page<CrawlJob> page = jobRepo.findBySourceIdOrderByCreatedAtDesc(
                source.getId(), PageRequest.of(0, 20));
        assertThat(page.getTotalElements()).isEqualTo(2);
    }

    // ── Native atomic counter increments ──────────────────────────────────────

    @Test
    void incrementPagesCrawled_atomicallyIncrementsCounter() {
        CrawlJob j = job(CrawlStatus.RUNNING);
        assertThat(j.getPagesCrawled()).isZero();

        jobRepo.incrementPagesCrawled(j.getId());
        jobRepo.incrementPagesCrawled(j.getId());
        em.flush();
        em.clear(); // evict from L1 cache to force a DB read

        CrawlJob reloaded = jobRepo.findById(j.getId()).orElseThrow();
        assertThat(reloaded.getPagesCrawled()).isEqualTo(2);
    }

    @Test
    void incrementPagesFailed_atomicallyIncrementsCounter() {
        CrawlJob j = job(CrawlStatus.RUNNING);

        jobRepo.incrementPagesFailed(j.getId());
        em.flush();
        em.clear();

        assertThat(jobRepo.findById(j.getId()).orElseThrow().getPagesFailed()).isEqualTo(1);
    }

    @Test
    void incrementItemsFound_atomicallyIncrementsCounter() {
        CrawlJob j = job(CrawlStatus.RUNNING);

        jobRepo.incrementItemsFound(j.getId());
        jobRepo.incrementItemsFound(j.getId());
        jobRepo.incrementItemsFound(j.getId());
        em.flush();
        em.clear();

        assertThat(jobRepo.findById(j.getId()).orElseThrow().getItemsFound()).isEqualTo(3);
    }

    @Test
    void updateStatus_changesJobStatus() {
        CrawlJob j = job(CrawlStatus.RUNNING);

        jobRepo.updateStatus(j.getId(), CrawlStatus.PAUSED.name());
        em.flush();
        em.clear();

        assertThat(jobRepo.findById(j.getId()).orElseThrow().getStatus())
                .isEqualTo(CrawlStatus.PAUSED);
    }

    @Test
    void updateCheckpoint_storesCheckpointJson() {
        CrawlJob j = job(CrawlStatus.RUNNING);
        String checkpoint = "{\"pending\":[\"http://example.com/page2\"]}";

        jobRepo.updateCheckpoint(j.getId(), checkpoint);
        em.flush();
        em.clear();

        assertThat(jobRepo.findById(j.getId()).orElseThrow().getCheckpoint())
                .isEqualTo(checkpoint);
    }

    @TestConfiguration
    @EnableJpaAuditing
    static class AuditingConfig {}
}
