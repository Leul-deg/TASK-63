package com.reslife.api.domain.crawler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CrawlJobServiceTest {

    private final CrawlJobRepository jobRepo = mock(CrawlJobRepository.class);
    private final CrawlPageRepository pageRepo = mock(CrawlPageRepository.class);
    private final CrawlEngineService engine = mock(CrawlEngineService.class);

    private CrawlJobService service;
    private UUID jobId;
    private CrawlJob job;

    @BeforeEach
    void setUp() {
        service = new CrawlJobService(jobRepo, pageRepo, engine);
        jobId = UUID.randomUUID();
        job = new CrawlJob();
        CrawlSource source = new CrawlSource();
        source.setName("Local source");
        job.setSource(source);
        job.setTriggerType(TriggerType.MANUAL);
        job.setStatus(CrawlStatus.RUNNING);

        when(jobRepo.findById(jobId)).thenReturn(Optional.of(job));
        when(jobRepo.save(any(CrawlJob.class))).thenAnswer(inv -> inv.getArgument(0));
        when(jobRepo.findAllByOrderByCreatedAtDesc(any())).thenReturn(new PageImpl<>(List.of(job), PageRequest.of(0, 20), 1));
    }

    @Test
    void pauseJob_fallsBackToPersistedPausedStateWhenEngineDoesNotKnowJob() {
        when(engine.pauseJob(jobId)).thenReturn(false);

        JobResponse response = service.pauseJob(jobId);

        assertEquals(CrawlStatus.PAUSED, job.getStatus());
        assertEquals(CrawlStatus.PAUSED.name(), response.status());
        verify(jobRepo).save(job);
    }

    @Test
    void cancelJob_fallsBackToPersistedCancelledStateWhenEngineDoesNotKnowJob() {
        when(engine.cancelJob(jobId)).thenReturn(false);

        JobResponse response = service.cancelJob(jobId);

        assertEquals(CrawlStatus.CANCELLED, job.getStatus());
        assertEquals(CrawlStatus.CANCELLED.name(), response.status());
        verify(jobRepo).save(job);
    }

    @Test
    void resumeJob_submitsPausedJobBackToEngine() {
        job.setStatus(CrawlStatus.PAUSED);

        JobResponse response = service.resumeJob(jobId);

        assertEquals(CrawlStatus.PENDING, job.getStatus());
        assertEquals(CrawlStatus.PENDING.name(), response.status());
        verify(engine).submit(jobId);
    }

    @Test
    void engineStatus_passesThroughEngineSnapshot() {
        CrawlEngineService.EngineStatus status = new CrawlEngineService.EngineStatus(5, 2, List.of(jobId));
        when(engine.currentStatus()).thenReturn(status);

        CrawlEngineService.EngineStatus response = service.engineStatus();

        assertSame(status, response);
    }
}
