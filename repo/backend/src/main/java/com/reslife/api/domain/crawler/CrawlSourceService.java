package com.reslife.api.domain.crawler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reslife.api.domain.user.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * CRUD operations for {@link CrawlSource}, keeping the in-memory scheduler in sync.
 *
 * <p>Every mutating operation calls the appropriate {@link CrawlSchedulerService}
 * method to ensure scheduled futures are added, replaced, or cancelled as needed.
 */
@Service
@Transactional(readOnly = true)
public class CrawlSourceService {

    private static final Logger log = LoggerFactory.getLogger(CrawlSourceService.class);

    private final CrawlSourceRepository  sourceRepo;
    private final CrawlSchedulerService  scheduler;
    private final CrawlEngineService     engine;
    private final CrawlJobRepository     jobRepo;
    private final EntityManager          em;
    private final ObjectMapper           objectMapper;

    public CrawlSourceService(CrawlSourceRepository sourceRepo,
                               CrawlSchedulerService scheduler,
                               CrawlEngineService engine,
                               CrawlJobRepository jobRepo,
                               EntityManager em,
                               ObjectMapper objectMapper) {
        this.sourceRepo = sourceRepo;
        this.scheduler  = scheduler;
        this.engine     = engine;
        this.jobRepo    = jobRepo;
        this.em         = em;
        this.objectMapper = objectMapper;
    }

    // ── Read ───────────────────────────────────────────────────────────────

    public Page<SourceResponse> listSources(Pageable pageable) {
        return sourceRepo.findAllByOrderByCreatedAtDesc(pageable).map(SourceResponse::from);
    }

    public SourceResponse getSource(UUID id) {
        return SourceResponse.from(requireSource(id));
    }

    // ── Write ──────────────────────────────────────────────────────────────

    @Transactional
    public SourceResponse createSource(CreateSourceRequest req, UUID createdByUserId) {
        CrawlSource source = new CrawlSource();
        applyRequest(source, req);

        if (createdByUserId != null) {
            source.setCreatedBy(em.getReference(User.class, createdByUserId));
        }

        sourceRepo.save(source);
        scheduler.rescheduleSource(source);

        log.info("Created crawl source '{}' ({})", source.getName(), source.getId());
        return SourceResponse.from(source);
    }

    @Transactional
    public SourceResponse updateSource(UUID id, CreateSourceRequest req) {
        CrawlSource source = requireSource(id);
        applyRequest(source, req);
        sourceRepo.save(source);
        scheduler.rescheduleSource(source);

        log.info("Updated crawl source '{}' ({})", source.getName(), source.getId());
        return SourceResponse.from(source);
    }

    @Transactional
    public void deleteSource(UUID id) {
        CrawlSource source = requireSource(id);
        scheduler.unscheduleSource(id);
        source.softDelete();
        sourceRepo.save(source);
        log.info("Soft-deleted crawl source '{}' ({})", source.getName(), id);
    }

    // ── Manual trigger ─────────────────────────────────────────────────────

    @Transactional
    public JobResponse triggerManual(UUID sourceId, UUID triggeredByUserId) {
        CrawlSource source = requireSource(sourceId);
        if (!source.isActive()) {
            throw new IllegalStateException("Source '" + source.getName() + "' is not active");
        }

        boolean alreadyRunning = jobRepo.findFirstBySourceIdAndStatusIn(
                sourceId, java.util.List.of(CrawlStatus.PENDING, CrawlStatus.RUNNING)).isPresent();
        if (alreadyRunning) {
            throw new IllegalStateException("A job is already running for source '" + source.getName() + "'");
        }

        CrawlJob job = new CrawlJob();
        job.setSource(source);
        job.setTriggerType(TriggerType.MANUAL);
        job.setStatus(CrawlStatus.PENDING);
        if (triggeredByUserId != null) {
            job.setTriggeredBy(em.getReference(User.class, triggeredByUserId));
        }
        jobRepo.save(job);

        engine.submit(job.getId());
        log.info("Manual trigger for source '{}' — job {}", source.getName(), job.getId());
        return JobResponse.from(job);
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private CrawlSource requireSource(UUID id) {
        return sourceRepo.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("CrawlSource not found: " + id));
    }

    private void applyRequest(CrawlSource source, CreateSourceRequest req) {
        List<String> keywords = parseKeywords(req.keywords());
        CrawlConfig crawlConfig = parseConfig(req.crawlConfig());
        crawlConfig.setKeywords(keywords);

        source.setName(req.name());
        source.setBaseUrl(req.baseUrl());
        source.setSiteType(req.siteType());
        source.setDescription(req.description());
        source.setCity(req.city());
        source.setKeywords(joinKeywords(keywords));
        source.setCrawlConfig(serializeConfig(crawlConfig));
        source.setScheduleCron(req.scheduleCron());
        source.setScheduleIntervalSeconds(req.scheduleIntervalSeconds());
        source.setDelayMsBetweenRequests(req.delayMsBetweenRequests());
        source.setMaxDepth(req.maxDepth());
        source.setMaxPages(req.maxPages());
        source.setActive(req.active());
    }

    private CrawlConfig parseConfig(String json) {
        if (json == null || json.isBlank()) {
            return new CrawlConfig();
        }
        try {
            return objectMapper.readValue(json, CrawlConfig.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("crawlConfig must be valid JSON");
        }
    }

    private String serializeConfig(CrawlConfig crawlConfig) {
        try {
            return objectMapper.writeValueAsString(crawlConfig);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot serialize crawlConfig", e);
        }
    }

    private List<String> parseKeywords(String rawKeywords) {
        if (rawKeywords == null || rawKeywords.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(rawKeywords.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .distinct()
                .toList();
    }

    private String joinKeywords(List<String> keywords) {
        return keywords.isEmpty() ? null : keywords.stream().collect(Collectors.joining(", "));
    }
}
