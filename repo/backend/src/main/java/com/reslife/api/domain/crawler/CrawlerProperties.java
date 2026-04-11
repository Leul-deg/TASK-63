package com.reslife.api.domain.crawler;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Engine-level configuration, bound from properties prefixed {@code reslife.crawler}.
 *
 * <pre>
 * reslife.crawler.max-concurrent=5
 * reslife.crawler.fetch-timeout-seconds=30
 * reslife.crawler.checkpoint-interval-pages=10
 * reslife.crawler.user-agent=ResLife-Crawler/1.0
 * </pre>
 */
@ConfigurationProperties(prefix = "reslife.crawler")
public class CrawlerProperties {

    /** Maximum number of crawl jobs that may run concurrently. Default: 5. */
    private int maxConcurrent = 5;

    /** Per-request HTTP connect + read timeout in seconds. Default: 30. */
    private int fetchTimeoutSeconds = 30;

    /**
     * How many pages to crawl between checkpoint saves.
     * Lower values mean more frequent saves and less work lost on interruption. Default: 10.
     */
    private int checkpointIntervalPages = 10;

    /** User-Agent header sent with every outbound request. */
    private String userAgent = "ResLife-Crawler/1.0";

    public int getMaxConcurrent()                  { return maxConcurrent; }
    public void setMaxConcurrent(int v)            { this.maxConcurrent = v; }

    public int getFetchTimeoutSeconds()             { return fetchTimeoutSeconds; }
    public void setFetchTimeoutSeconds(int v)      { this.fetchTimeoutSeconds = v; }

    public int getCheckpointIntervalPages()         { return checkpointIntervalPages; }
    public void setCheckpointIntervalPages(int v)  { this.checkpointIntervalPages = v; }

    public String getUserAgent()                   { return userAgent; }
    public void setUserAgent(String v)             { this.userAgent = v; }
}
