package com.reslife.api.domain.crawler;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

/**
 * Manages dynamic per-source schedules.
 *
 * <p>On startup ({@link #init}) all active, scheduled sources are registered with
 * Spring's {@link TaskScheduler}.  When a source is created, updated, or deleted
 * via the admin API, {@link #rescheduleSource} / {@link #unscheduleSource} keep
 * the in-memory schedule map in sync.
 *
 * <p>The actual job creation and submission is delegated to
 * {@link #triggerScheduledJob} which checks that no job for the same source is
 * already running before submitting a new one.
 */
@Service
public class CrawlSchedulerService {

    private static final Logger log = LoggerFactory.getLogger(CrawlSchedulerService.class);

    private final ConcurrentHashMap<UUID, ScheduledFuture<?>> schedules = new ConcurrentHashMap<>();

    private final TaskScheduler       taskScheduler;
    private final CrawlEngineService  engine;
    private final CrawlSourceRepository sourceRepo;
    private final CrawlJobRepository  jobRepo;

    public CrawlSchedulerService(TaskScheduler taskScheduler,
                                  CrawlEngineService engine,
                                  CrawlSourceRepository sourceRepo,
                                  CrawlJobRepository jobRepo) {
        this.taskScheduler = taskScheduler;
        this.engine        = engine;
        this.sourceRepo    = sourceRepo;
        this.jobRepo       = jobRepo;
    }

    // ── Startup ────────────────────────────────────────────────────────────

    @PostConstruct
    public void init() {
        List<CrawlSource> active = sourceRepo.findByActiveTrueOrderByNameAsc();
        active.stream()
              .filter(CrawlSource::hasSchedule)
              .forEach(this::scheduleSource);
        log.info("Crawl scheduler initialised — {} source(s) scheduled", schedules.size());
    }

    // ── Schedule management ────────────────────────────────────────────────

    /**
     * Registers or replaces the schedule for a source.
     * No-op if the source has no cron expression and no interval.
     */
    public void rescheduleSource(CrawlSource source) {
        unscheduleSource(source.getId());
        if (source.isActive() && source.hasSchedule()) {
            scheduleSource(source);
        }
    }

    /** Cancels the scheduled future for a source (if any). */
    public void unscheduleSource(UUID sourceId) {
        ScheduledFuture<?> existing = schedules.remove(sourceId);
        if (existing != null) {
            existing.cancel(false);
            log.debug("Unscheduled source {}", sourceId);
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private void scheduleSource(CrawlSource source) {
        Runnable task = () -> triggerScheduledJob(source.getId());

        ScheduledFuture<?> future;
        if (source.getScheduleCron() != null) {
            future = taskScheduler.schedule(task, new CronTrigger(source.getScheduleCron()));
            log.info("Source '{}' scheduled with cron '{}'", source.getName(), source.getScheduleCron());
        } else {
            Duration interval = Duration.ofSeconds(source.getScheduleIntervalSeconds());
            future = taskScheduler.scheduleAtFixedRate(task, interval);
            log.info("Source '{}' scheduled every {} seconds", source.getName(), source.getScheduleIntervalSeconds());
        }
        schedules.put(source.getId(), future);
    }

    @Transactional
    void triggerScheduledJob(UUID sourceId) {
        CrawlSource source = sourceRepo.findById(sourceId).orElse(null);
        if (source == null || !source.isActive()) {
            unscheduleSource(sourceId);
            return;
        }

        // Prevent duplicate concurrent runs for the same source
        boolean alreadyRunning = jobRepo.findFirstBySourceIdAndStatusIn(
                sourceId, List.of(CrawlStatus.PENDING, CrawlStatus.RUNNING)).isPresent();
        if (alreadyRunning) {
            log.debug("Skipping scheduled trigger for '{}' — a job is already active", source.getName());
            return;
        }

        CrawlJob job = new CrawlJob();
        job.setSource(source);
        job.setTriggerType(TriggerType.SCHEDULED);
        job.setStatus(CrawlStatus.PENDING);
        jobRepo.save(job);

        engine.submit(job.getId());
        log.info("Scheduled trigger fired for source '{}' — job {}", source.getName(), job.getId());
    }
}
