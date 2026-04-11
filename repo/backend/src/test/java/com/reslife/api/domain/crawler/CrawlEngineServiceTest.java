package com.reslife.api.domain.crawler;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CrawlEngineServiceTest {

    private final CrawlJobRepository jobRepo = mock(CrawlJobRepository.class);
    private final CrawlPageRepository pageRepo = mock(CrawlPageRepository.class);
    private final CrawlItemRepository itemRepo = mock(CrawlItemRepository.class);
    private final CrawlSourceRepository sourceRepo = mock(CrawlSourceRepository.class);
    private final CrawlFetcherService fetcher = mock(CrawlFetcherService.class);

    private CrawlEngineService engine;

    @BeforeEach
    void setUp() {
        CrawlerProperties props = new CrawlerProperties();
        props.setMaxConcurrent(2);
        props.setCheckpointIntervalPages(5);
        engine = new CrawlEngineService(
                jobRepo, pageRepo, itemRepo, sourceRepo, fetcher, new ObjectMapper(), props);
    }

    @Test
    void currentStatus_reportsConfiguredConcurrencyWhenIdle() {
        CrawlEngineService.EngineStatus status = engine.currentStatus();

        assertEquals(2, status.maxConcurrent());
        assertEquals(0, status.activeWorkers());
        assertTrue(status.runningJobIds().isEmpty());
    }

    @Test
    void pauseAndCancel_returnFalseWhenJobIsUnknown() {
        UUID jobId = UUID.randomUUID();

        assertFalse(engine.pauseJob(jobId));
        assertFalse(engine.cancelJob(jobId));
    }

    @Test
    void submit_rejectsDuplicateQueuedJobIds() throws Exception {
        UUID jobId = UUID.randomUUID();
        CrawlJobExecution exec = mock(CrawlJobExecution.class);
        activeExecutions().put(jobId, exec);

        assertThrows(IllegalStateException.class, () -> engine.submit(jobId));
    }

    @Test
    void pauseAndCancel_signalKnownExecution() throws Exception {
        UUID jobId = UUID.randomUUID();
        CrawlJobExecution exec = mock(CrawlJobExecution.class);
        activeExecutions().put(jobId, exec);

        assertTrue(engine.pauseJob(jobId));
        assertTrue(engine.cancelJob(jobId));
        verify(exec).requestPause();
        verify(exec).requestCancel();

        CrawlEngineService.EngineStatus status = engine.currentStatus();
        assertEquals(List.of(jobId), status.runningJobIds());
    }

    @SuppressWarnings("unchecked")
    private ConcurrentHashMap<UUID, CrawlJobExecution> activeExecutions() throws Exception {
        Field field = CrawlEngineService.class.getDeclaredField("activeExecutions");
        field.setAccessible(true);
        return (ConcurrentHashMap<UUID, CrawlJobExecution>) field.get(engine);
    }
}
