package com.reslife.api.domain.crawler;

import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Query and control operations for crawl jobs.
 *
 * <p>Pause/resume/cancel are delegated to {@link CrawlEngineService} which holds
 * the in-memory {@link CrawlJobExecution} map.  If the engine has no record of
 * the job (e.g. after a server restart) the DB status is updated directly so the
 * UI reflects the correct terminal state.
 */
@Service
@Transactional(readOnly = true)
public class CrawlJobService {

    private final CrawlJobRepository  jobRepo;
    private final CrawlPageRepository pageRepo;
    private final CrawlEngineService  engine;

    public CrawlJobService(CrawlJobRepository jobRepo,
                            CrawlPageRepository pageRepo,
                            CrawlEngineService engine) {
        this.jobRepo  = jobRepo;
        this.pageRepo = pageRepo;
        this.engine   = engine;
    }

    // ── Queries ────────────────────────────────────────────────────────────

    public Page<JobResponse> listJobs(Pageable pageable) {
        return jobRepo.findAllByOrderByCreatedAtDesc(pageable).map(JobResponse::from);
    }

    public Page<JobResponse> listJobsBySource(UUID sourceId, Pageable pageable) {
        return jobRepo.findBySourceIdOrderByCreatedAtDesc(sourceId, pageable).map(JobResponse::from);
    }

    public JobResponse getJob(UUID jobId) {
        return JobResponse.from(requireJob(jobId));
    }

    public Page<PageResponse> listPages(UUID jobId, Pageable pageable) {
        requireJob(jobId); // 404 guard
        return pageRepo.findByJobIdOrderByCreatedAtDesc(jobId, pageable).map(PageResponse::from);
    }

    public CrawlEngineService.EngineStatus engineStatus() {
        return engine.currentStatus();
    }

    // ── Control ────────────────────────────────────────────────────────────

    @Transactional
    public JobResponse pauseJob(UUID jobId) {
        CrawlJob job = requireJob(jobId);
        if (job.getStatus() != CrawlStatus.RUNNING) {
            throw new IllegalStateException("Job " + jobId + " is not RUNNING (status: " + job.getStatus() + ")");
        }
        boolean signalled = engine.pauseJob(jobId);
        if (!signalled) {
            // Engine doesn't know about this job — mark paused directly
            job.setStatus(CrawlStatus.PAUSED);
            jobRepo.save(job);
        }
        return JobResponse.from(jobRepo.findById(jobId).orElseThrow());
    }

    @Transactional
    public JobResponse resumeJob(UUID jobId) {
        CrawlJob job = requireJob(jobId);
        if (job.getStatus() != CrawlStatus.PAUSED) {
            throw new IllegalStateException("Job " + jobId + " is not PAUSED (status: " + job.getStatus() + ")");
        }
        job.setStatus(CrawlStatus.PENDING);
        jobRepo.save(job);
        engine.submit(jobId);
        return JobResponse.from(jobRepo.findById(jobId).orElseThrow());
    }

    @Transactional
    public JobResponse cancelJob(UUID jobId) {
        CrawlJob job = requireJob(jobId);
        if (!job.getStatus().isActive()) {
            throw new IllegalStateException("Job " + jobId + " is not active (status: " + job.getStatus() + ")");
        }
        boolean signalled = engine.cancelJob(jobId);
        if (!signalled) {
            job.setStatus(CrawlStatus.CANCELLED);
            jobRepo.save(job);
        }
        return JobResponse.from(jobRepo.findById(jobId).orElseThrow());
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private CrawlJob requireJob(UUID id) {
        return jobRepo.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("CrawlJob not found: " + id));
    }
}
