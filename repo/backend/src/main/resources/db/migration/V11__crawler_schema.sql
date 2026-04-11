-- V11: Multi-source data collection engine
--   crawl_sources  — configured data sources (schedules, throttles, crawl rules)
--   crawl_jobs     — one execution run per source trigger
--   crawl_pages    — one row per URL fetched within a job
--   crawl_items    — extracted data items (append-only, deduplicated by hash)

CREATE TABLE crawl_sources (
    id                          UUID          PRIMARY KEY,
    name                        VARCHAR(255)  NOT NULL,
    base_url                    VARCHAR(1000) NOT NULL,
    site_type                   VARCHAR(20)   NOT NULL DEFAULT 'HTML',
    description                 TEXT,
    city                        VARCHAR(100),
    keywords                    TEXT,                                  -- JSON array
    crawl_config                TEXT          NOT NULL DEFAULT '{}',   -- JSON (CrawlConfig)
    schedule_cron               VARCHAR(100),                          -- Spring cron: "0 */6 * * * *"
    schedule_interval_seconds   BIGINT,                                -- alternative to cron
    requests_per_second         NUMERIC(6,2)  NOT NULL DEFAULT 1.0,
    delay_ms_between_requests   INT           NOT NULL DEFAULT 1000,
    max_depth                   INT           NOT NULL DEFAULT 3,
    max_pages                   INT           NOT NULL DEFAULT 100,
    is_active                   BOOLEAN       NOT NULL DEFAULT TRUE,
    last_crawled_at             TIMESTAMP WITH TIME ZONE,
    created_by_user_id          UUID          REFERENCES users(id),
    created_at                  TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at                  TIMESTAMP WITH TIME ZONE NOT NULL,
    deleted_at                  TIMESTAMP WITH TIME ZONE
);

CREATE TABLE crawl_jobs (
    id                    UUID         PRIMARY KEY,
    source_id             UUID         NOT NULL REFERENCES crawl_sources(id),
    trigger_type          VARCHAR(20)  NOT NULL
                              CHECK (trigger_type IN ('SCHEDULED','MANUAL')),
    status                VARCHAR(20)  NOT NULL DEFAULT 'PENDING'
                              CHECK (status IN ('PENDING','RUNNING','PAUSED','COMPLETED','FAILED','CANCELLED')),
    pages_crawled         INT          NOT NULL DEFAULT 0,
    pages_skipped         INT          NOT NULL DEFAULT 0,
    pages_failed          INT          NOT NULL DEFAULT 0,
    items_found           INT          NOT NULL DEFAULT 0,
    started_at            TIMESTAMP WITH TIME ZONE,
    paused_at             TIMESTAMP WITH TIME ZONE,
    finished_at           TIMESTAMP WITH TIME ZONE,
    checkpoint            TEXT,                          -- JSON (CrawlCheckpoint)
    error_message         TEXT,
    triggered_by_user_id  UUID         REFERENCES users(id),
    created_at            TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at            TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE crawl_pages (
    id              UUID         PRIMARY KEY,
    job_id          UUID         NOT NULL REFERENCES crawl_jobs(id),
    url             VARCHAR(2000) NOT NULL,
    url_hash        CHAR(64)     NOT NULL,               -- SHA-256(url) for indexing
    status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING'
                        CHECK (status IN ('PENDING','FETCHING','FETCHED','SKIPPED','ERROR')),
    http_status     INT,
    content_hash    CHAR(64),                            -- SHA-256(body) for incremental updates
    content_length  INT,
    depth           INT          NOT NULL DEFAULT 0,
    error_message   TEXT,
    fetched_at      TIMESTAMP WITH TIME ZONE,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_crawl_pages_job_url UNIQUE (job_id, url_hash)
);

CREATE TABLE crawl_items (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    source_id     UUID         NOT NULL REFERENCES crawl_sources(id),
    job_id        UUID         NOT NULL REFERENCES crawl_jobs(id),
    page_id       UUID         REFERENCES crawl_pages(id),
    url           VARCHAR(2000),
    data_hash     CHAR(64)     NOT NULL,                 -- SHA-256 of normalized item JSON
    raw_data      TEXT         NOT NULL,
    is_new        BOOLEAN      NOT NULL DEFAULT TRUE,    -- false = seen before (updated)
    extracted_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

-- Indexes
CREATE INDEX idx_crawl_sources_active      ON crawl_sources(is_active) WHERE deleted_at IS NULL;
CREATE INDEX idx_crawl_sources_deleted     ON crawl_sources(deleted_at) WHERE deleted_at IS NULL;
CREATE INDEX idx_crawl_jobs_source_status  ON crawl_jobs(source_id, status);
CREATE INDEX idx_crawl_jobs_status_time    ON crawl_jobs(status, created_at DESC);
CREATE INDEX idx_crawl_pages_job_status    ON crawl_pages(job_id, status);
CREATE INDEX idx_crawl_pages_url_hash      ON crawl_pages(url_hash);
CREATE INDEX idx_crawl_items_source_hash   ON crawl_items(source_id, data_hash);
CREATE INDEX idx_crawl_items_job           ON crawl_items(job_id);
