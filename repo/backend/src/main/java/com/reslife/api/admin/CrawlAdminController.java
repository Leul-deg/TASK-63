package com.reslife.api.admin;

import com.reslife.api.domain.crawler.*;
import com.reslife.api.security.ReslifeUserDetails;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Admin-only REST endpoints for the multi-source data collection engine.
 *
 * <pre>
 * GET    /api/admin/crawl/sources                    — list sources (paged)
 * POST   /api/admin/crawl/sources                    — create source
 * GET    /api/admin/crawl/sources/{id}               — get source
 * PUT    /api/admin/crawl/sources/{id}               — update source
 * DELETE /api/admin/crawl/sources/{id}               — soft-delete source
 * POST   /api/admin/crawl/sources/{id}/trigger       — manual job trigger
 * GET    /api/admin/crawl/sources/{id}/jobs          — jobs for source (paged)
 *
 * GET    /api/admin/crawl/jobs                       — all jobs (paged)
 * GET    /api/admin/crawl/jobs/{id}                  — job detail
 * POST   /api/admin/crawl/jobs/{id}/pause            — pause running job
 * POST   /api/admin/crawl/jobs/{id}/resume           — resume paused job
 * POST   /api/admin/crawl/jobs/{id}/cancel           — cancel active job
 * GET    /api/admin/crawl/jobs/{id}/pages            — pages fetched by job (paged)
 *
 * GET    /api/admin/crawl/engine/status              — engine concurrency snapshot
 * </pre>
 */
@RestController
@RequestMapping("/api/admin/crawl")
public class CrawlAdminController {

    private final CrawlSourceService sourceService;
    private final CrawlJobService    jobService;

    public CrawlAdminController(CrawlSourceService sourceService,
                                 CrawlJobService jobService) {
        this.sourceService = sourceService;
        this.jobService    = jobService;
    }

    // ── Sources ────────────────────────────────────────────────────────────

    @GetMapping("/sources")
    public Page<SourceResponse> listSources(@PageableDefault(size = 20) Pageable pageable) {
        return sourceService.listSources(pageable);
    }

    @PostMapping("/sources")
    @ResponseStatus(HttpStatus.CREATED)
    public SourceResponse createSource(
            @Valid @RequestBody CreateSourceRequest req,
            @AuthenticationPrincipal ReslifeUserDetails principal) {
        return sourceService.createSource(req, principal.getUserId());
    }

    @GetMapping("/sources/{id}")
    public SourceResponse getSource(@PathVariable UUID id) {
        return sourceService.getSource(id);
    }

    @PutMapping("/sources/{id}")
    public SourceResponse updateSource(@PathVariable UUID id,
                                        @Valid @RequestBody CreateSourceRequest req) {
        return sourceService.updateSource(id, req);
    }

    @DeleteMapping("/sources/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteSource(@PathVariable UUID id) {
        sourceService.deleteSource(id);
    }

    @PostMapping("/sources/{id}/trigger")
    @ResponseStatus(HttpStatus.CREATED)
    public JobResponse triggerManual(
            @PathVariable UUID id,
            @AuthenticationPrincipal ReslifeUserDetails principal) {
        return sourceService.triggerManual(id, principal.getUserId());
    }

    @GetMapping("/sources/{id}/jobs")
    public Page<JobResponse> jobsForSource(@PathVariable UUID id,
                                            @PageableDefault(size = 20) Pageable pageable) {
        return jobService.listJobsBySource(id, pageable);
    }

    // ── Jobs ───────────────────────────────────────────────────────────────

    @GetMapping("/jobs")
    public Page<JobResponse> listJobs(@PageableDefault(size = 20) Pageable pageable) {
        return jobService.listJobs(pageable);
    }

    @GetMapping("/jobs/{id}")
    public JobResponse getJob(@PathVariable UUID id) {
        return jobService.getJob(id);
    }

    @PostMapping("/jobs/{id}/pause")
    public JobResponse pauseJob(@PathVariable UUID id) {
        return jobService.pauseJob(id);
    }

    @PostMapping("/jobs/{id}/resume")
    public JobResponse resumeJob(@PathVariable UUID id) {
        return jobService.resumeJob(id);
    }

    @PostMapping("/jobs/{id}/cancel")
    public JobResponse cancelJob(@PathVariable UUID id) {
        return jobService.cancelJob(id);
    }

    @GetMapping("/jobs/{id}/pages")
    public Page<PageResponse> listPages(@PathVariable UUID id,
                                         @PageableDefault(size = 50) Pageable pageable) {
        return jobService.listPages(id, pageable);
    }

    // ── Engine status ──────────────────────────────────────────────────────

    @GetMapping("/engine/status")
    public CrawlEngineService.EngineStatus engineStatus() {
        return jobService.engineStatus();
    }
}
