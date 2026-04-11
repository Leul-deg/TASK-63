package com.reslife.api.domain.crawler;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Central execution engine for crawl jobs.
 *
 * <h3>Concurrency model</h3>
 * <p>A {@link Semaphore} with {@link CrawlerProperties#getMaxConcurrent()} permits
 * limits the number of jobs that actively fetch pages at the same time (default 5).
 * Jobs are submitted to a virtual-thread executor, so they queue cheaply until a
 * semaphore permit is available rather than blocking an OS thread.
 *
 * <h3>Pause / Cancel</h3>
 * <p>Each running job is represented by a {@link CrawlJobExecution} entry in
 * {@link #activeExecutions}.  Calling {@link #pauseJob} or {@link #cancelJob}
 * sets an atomic flag on the execution; the crawl loop checks this flag at the
 * start of every iteration (not mid-fetch) to ensure the DB is left consistent.
 *
 * <h3>Resource protection</h3>
 * <ul>
 *   <li>Virtual threads are used so thousands of queued jobs don't exhaust OS threads.</li>
 *   <li>The semaphore caps actual concurrent network activity.</li>
 *   <li>Target URLs are validated as local-network before any request is made
 *       (enforced in {@link CrawlFetcherService}).</li>
 *   <li>{@link PreDestroy} drains the executor gracefully on shutdown.</li>
 * </ul>
 */
@Service
public class CrawlEngineService {

    private static final Logger log = LoggerFactory.getLogger(CrawlEngineService.class);

    private final Semaphore                              semaphore;
    private final ExecutorService                        executor;
    private final ConcurrentHashMap<UUID, CrawlJobExecution> activeExecutions = new ConcurrentHashMap<>();

    private final CrawlJobRepository    jobRepo;
    private final CrawlPageRepository   pageRepo;
    private final CrawlItemRepository   itemRepo;
    private final CrawlSourceRepository sourceRepo;
    private final CrawlFetcherService   fetcher;
    private final ObjectMapper          objectMapper;
    private final int                   checkpointInterval;
    private final int                   maxConcurrent;

    public CrawlEngineService(CrawlJobRepository jobRepo,
                               CrawlPageRepository pageRepo,
                               CrawlItemRepository itemRepo,
                               CrawlSourceRepository sourceRepo,
                               CrawlFetcherService fetcher,
                               ObjectMapper objectMapper,
                               CrawlerProperties props) {
        this.jobRepo            = jobRepo;
        this.pageRepo           = pageRepo;
        this.itemRepo           = itemRepo;
        this.sourceRepo         = sourceRepo;
        this.fetcher            = fetcher;
        this.objectMapper       = objectMapper;
        this.checkpointInterval = props.getCheckpointIntervalPages();
        this.maxConcurrent      = props.getMaxConcurrent();
        this.semaphore          = new Semaphore(maxConcurrent, true); // fair ordering
        this.executor           = Executors.newVirtualThreadPerTaskExecutor();
        log.info("Crawl engine initialised (maxConcurrent={})", maxConcurrent);
    }

    // ── Submit ─────────────────────────────────────────────────────────────

    /**
     * Submits a job for execution.  Returns immediately; the job will start
     * as soon as a semaphore permit is available.
     *
     * @throws IllegalStateException if the job is already actively running
     */
    public void submit(UUID jobId) {
        if (activeExecutions.containsKey(jobId)) {
            throw new IllegalStateException("Job " + jobId + " is already queued or running");
        }

        CrawlJob job = jobRepo.findById(jobId)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("CrawlJob not found: " + jobId));

        CrawlConfig config = parseConfig(job.getSource());

        CrawlJobExecution exec = new CrawlJobExecution(
                jobId, job.getSource(), config,
                jobRepo, pageRepo, itemRepo, sourceRepo,
                fetcher, objectMapper, checkpointInterval);

        activeExecutions.put(jobId, exec);

        executor.submit(() -> {
            boolean acquired = false;
            try {
                semaphore.acquire();          // blocks until a slot is free
                acquired = true;
                exec.run();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Job {} worker thread interrupted while waiting for semaphore", jobId);
            } finally {
                if (acquired) semaphore.release();
                activeExecutions.remove(jobId);
            }
        });

        log.info("Job {} submitted (queue depth ≈ {}, active {}/{})",
                jobId, activeExecutions.size(), maxConcurrent - semaphore.availablePermits(), maxConcurrent);
    }

    // ── Control ────────────────────────────────────────────────────────────

    /**
     * Signals a running job to pause at the next safe checkpoint.
     *
     * @return {@code true} if the signal was delivered; {@code false} if the job
     *         is not currently running in this engine instance
     */
    public boolean pauseJob(UUID jobId) {
        CrawlJobExecution exec = activeExecutions.get(jobId);
        if (exec == null) return false;
        exec.requestPause();
        log.info("Pause requested for job {}", jobId);
        return true;
    }

    /**
     * Signals a running job to stop immediately (after the current fetch completes).
     *
     * @return {@code true} if the signal was delivered
     */
    public boolean cancelJob(UUID jobId) {
        CrawlJobExecution exec = activeExecutions.get(jobId);
        if (exec == null) return false;
        exec.requestCancel();
        log.info("Cancel requested for job {}", jobId);
        return true;
    }

    // ── Status ─────────────────────────────────────────────────────────────

    /** Returns a snapshot of the engine's current operational state. */
    public EngineStatus currentStatus() {
        int active  = maxConcurrent - semaphore.availablePermits();
        return new EngineStatus(maxConcurrent, active, List.copyOf(activeExecutions.keySet()));
    }

    public record EngineStatus(int maxConcurrent, int activeWorkers, List<UUID> runningJobIds) {}

    // ── Lifecycle ──────────────────────────────────────────────────────────

    @PreDestroy
    void shutdown() {
        log.info("Crawl engine shutting down — signalling {} active jobs to cancel", activeExecutions.size());
        activeExecutions.values().forEach(CrawlJobExecution::requestCancel);
        executor.shutdown();
        try {
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private CrawlConfig parseConfig(CrawlSource source) {
        String json = source.getCrawlConfig();
        if (json == null || json.isBlank()) {
            CrawlConfig config = new CrawlConfig();
            applyLegacyKeywords(source, config);
            return config;
        }
        try {
            CrawlConfig config = objectMapper.readValue(json, CrawlConfig.class);
            applyLegacyKeywords(source, config);
            return config;
        } catch (Exception e) {
            log.warn("Source {} has invalid crawlConfig JSON — using defaults: {}", source.getId(), e.getMessage());
            CrawlConfig config = new CrawlConfig();
            applyLegacyKeywords(source, config);
            return config;
        }
    }

    private void applyLegacyKeywords(CrawlSource source, CrawlConfig config) {
        if (config.getKeywords() != null && !config.getKeywords().isEmpty()) {
            return;
        }
        String rawKeywords = source.getKeywords();
        if (rawKeywords == null || rawKeywords.isBlank()) {
            return;
        }
        config.setKeywords(java.util.Arrays.stream(rawKeywords.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .distinct()
                .toList());
    }
}
