package com.reslife.api.domain.crawler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CrawlSchedulerServiceTest {

    private final TaskScheduler        taskScheduler = mock(TaskScheduler.class);
    private final CrawlEngineService   engine        = mock(CrawlEngineService.class);
    private final CrawlSourceRepository sourceRepo   = mock(CrawlSourceRepository.class);
    private final CrawlJobRepository   jobRepo       = mock(CrawlJobRepository.class);

    private CrawlSchedulerService scheduler;

    private static final UUID SOURCE_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        scheduler = new CrawlSchedulerService(taskScheduler, engine, sourceRepo, jobRepo);
    }

    @SuppressWarnings("unchecked")
    private ScheduledFuture<Object> mockFuture() {
        return mock(ScheduledFuture.class);
    }

    private CrawlSource cronSource() {
        CrawlSource s = mock(CrawlSource.class);
        when(s.getId()).thenReturn(SOURCE_ID);
        when(s.getName()).thenReturn("Cron Source");
        when(s.isActive()).thenReturn(true);
        when(s.hasSchedule()).thenReturn(true);
        when(s.getScheduleCron()).thenReturn("0 * * * * *");
        when(s.getScheduleIntervalSeconds()).thenReturn(null);
        return s;
    }

    private CrawlSource intervalSource() {
        CrawlSource s = mock(CrawlSource.class);
        when(s.getId()).thenReturn(SOURCE_ID);
        when(s.getName()).thenReturn("Interval Source");
        when(s.isActive()).thenReturn(true);
        when(s.hasSchedule()).thenReturn(true);
        when(s.getScheduleCron()).thenReturn(null);
        when(s.getScheduleIntervalSeconds()).thenReturn(120L);
        return s;
    }

    // ── rescheduleSource ──────────────────────────────────────────────────────

    @Test
    void rescheduleSource_schedulesCronTrigger_whenSourceHasCron() {
        doReturn(mockFuture()).when(taskScheduler).schedule(any(Runnable.class), any(CronTrigger.class));

        scheduler.rescheduleSource(cronSource());

        verify(taskScheduler).schedule(any(Runnable.class), any(CronTrigger.class));
    }

    @Test
    void rescheduleSource_schedulesFixedRate_whenSourceHasInterval() {
        doReturn(mockFuture()).when(taskScheduler).scheduleAtFixedRate(any(Runnable.class), any(Duration.class));

        scheduler.rescheduleSource(intervalSource());

        verify(taskScheduler).scheduleAtFixedRate(any(Runnable.class), eq(Duration.ofSeconds(120)));
    }

    @Test
    void rescheduleSource_doesNotSchedule_whenSourceIsInactive() {
        CrawlSource inactive = mock(CrawlSource.class);
        when(inactive.getId()).thenReturn(SOURCE_ID);
        when(inactive.isActive()).thenReturn(false);
        when(inactive.hasSchedule()).thenReturn(true);

        scheduler.rescheduleSource(inactive);

        verifyNoInteractions(taskScheduler);
    }

    @Test
    void rescheduleSource_doesNotSchedule_whenSourceHasNoSchedule() {
        CrawlSource noSchedule = mock(CrawlSource.class);
        when(noSchedule.getId()).thenReturn(SOURCE_ID);
        when(noSchedule.isActive()).thenReturn(true);
        when(noSchedule.hasSchedule()).thenReturn(false);

        scheduler.rescheduleSource(noSchedule);

        verifyNoInteractions(taskScheduler);
    }

    @Test
    void rescheduleSource_cancelsPreviousSchedule_beforeRescheduling() {
        ScheduledFuture<Object> oldFuture = mockFuture();
        doReturn(oldFuture).when(taskScheduler).schedule(any(Runnable.class), any(CronTrigger.class));
        scheduler.rescheduleSource(cronSource());

        // Reschedule same source — old future must be cancelled
        ScheduledFuture<Object> newFuture = mockFuture();
        doReturn(newFuture).when(taskScheduler).schedule(any(Runnable.class), any(CronTrigger.class));
        scheduler.rescheduleSource(cronSource());

        verify(oldFuture).cancel(false);
    }

    // ── unscheduleSource ──────────────────────────────────────────────────────

    @Test
    void unscheduleSource_cancelsFuture_whenSourceIsScheduled() {
        ScheduledFuture<Object> future = mockFuture();
        doReturn(future).when(taskScheduler).schedule(any(Runnable.class), any(CronTrigger.class));
        scheduler.rescheduleSource(cronSource());

        scheduler.unscheduleSource(SOURCE_ID);

        verify(future).cancel(false);
    }

    @Test
    void unscheduleSource_isNoOp_whenSourceWasNeverScheduled() {
        scheduler.unscheduleSource(UUID.randomUUID());
        // No exception expected
    }

    // ── triggerScheduledJob ───────────────────────────────────────────────────

    @Test
    void triggerScheduledJob_doesNothing_whenSourceNotFound() {
        when(sourceRepo.findById(SOURCE_ID)).thenReturn(Optional.empty());

        scheduler.triggerScheduledJob(SOURCE_ID);

        verifyNoInteractions(jobRepo);
        verifyNoInteractions(engine);
    }

    @Test
    void triggerScheduledJob_doesNothing_whenSourceIsInactive() {
        CrawlSource inactive = mock(CrawlSource.class);
        when(inactive.isActive()).thenReturn(false);
        when(sourceRepo.findById(SOURCE_ID)).thenReturn(Optional.of(inactive));

        scheduler.triggerScheduledJob(SOURCE_ID);

        verifyNoInteractions(jobRepo);
        verifyNoInteractions(engine);
    }

    @Test
    void triggerScheduledJob_skipsSubmit_whenJobAlreadyRunning() {
        CrawlSource source = cronSource();
        when(sourceRepo.findById(SOURCE_ID)).thenReturn(Optional.of(source));
        when(jobRepo.findFirstBySourceIdAndStatusIn(eq(SOURCE_ID), anyList()))
                .thenReturn(Optional.of(new CrawlJob()));

        scheduler.triggerScheduledJob(SOURCE_ID);

        verify(jobRepo, never()).save(any());
        verifyNoInteractions(engine);
    }

    @Test
    void triggerScheduledJob_createsJobAndSubmitsToEngine_whenNoJobRunning() {
        CrawlSource source = cronSource();
        when(sourceRepo.findById(SOURCE_ID)).thenReturn(Optional.of(source));
        when(jobRepo.findFirstBySourceIdAndStatusIn(any(), any())).thenReturn(Optional.empty());
        when(jobRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        scheduler.triggerScheduledJob(SOURCE_ID);

        verify(jobRepo).save(any(CrawlJob.class));
        verify(engine).submit(any());
    }
}
