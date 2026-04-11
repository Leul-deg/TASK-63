package com.reslife.api.config;

import com.reslife.api.domain.crawler.CrawlerProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * Infrastructure beans for the multi-source data collection engine.
 *
 * <p>Provides:
 * <ul>
 *   <li>A {@link TaskScheduler} for dynamic per-source cron/interval schedules.</li>
 *   <li>Binding of {@link CrawlerProperties} from {@code reslife.crawler.*}.</li>
 * </ul>
 */
@Configuration
@EnableConfigurationProperties(CrawlerProperties.class)
public class CrawlerConfig {

    @Bean
    public TaskScheduler crawlTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(4);
        scheduler.setThreadNamePrefix("crawl-sched-");
        scheduler.setWaitForTasksToCompleteOnShutdown(false);
        scheduler.initialize();
        return scheduler;
    }
}
