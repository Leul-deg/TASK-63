package com.reslife.api.domain.crawler;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CrawlSourceServiceTest {

    private final CrawlSourceRepository sourceRepo = mock(CrawlSourceRepository.class);
    private final CrawlSchedulerService scheduler = mock(CrawlSchedulerService.class);
    private final CrawlEngineService engine = mock(CrawlEngineService.class);
    private final CrawlJobRepository jobRepo = mock(CrawlJobRepository.class);
    private final EntityManager entityManager = mock(EntityManager.class);

    private CrawlSourceService service;

    @BeforeEach
    void setUp() {
        service = new CrawlSourceService(
                sourceRepo,
                scheduler,
                engine,
                jobRepo,
                entityManager,
                new ObjectMapper());
        when(sourceRepo.save(any(CrawlSource.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void createSource_persistsKeywordsIntoBothLegacyAndRuntimeConfigFields() throws Exception {
        SourceResponse created = service.createSource(new CreateSourceRequest(
                "Local source",
                "http://intranet.local/list",
                SiteType.HTML,
                null,
                "Addis",
                "vacancy, available",
                "{}",
                null,
                3600L,
                1000,
                3,
                100,
                true
        ), null);

        assertEquals("vacancy, available", created.keywords());

        CrawlConfig config = new ObjectMapper().readValue(created.crawlConfig(), CrawlConfig.class);
        assertEquals(2, config.getKeywords().size());
        assertTrue(config.getKeywords().contains("vacancy"));
        assertTrue(config.getKeywords().contains("available"));
    }
}
