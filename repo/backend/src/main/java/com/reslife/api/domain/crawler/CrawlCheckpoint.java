package com.reslife.api.domain.crawler;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Serializable snapshot of a crawl job's pending work queue.
 *
 * <p>Stored as JSON in {@code crawl_jobs.checkpoint}.  The visited-URL set is
 * reconstructed from {@code crawl_pages} rows rather than stored here, keeping
 * the checkpoint compact even for large crawls.
 *
 * <p>When a job resumes, the engine dequeues from {@link #pendingQueue} and
 * rebuilds the visited set by querying all pages with a terminal status for
 * this job.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class CrawlCheckpoint {

    private List<PendingTarget> pendingQueue = new ArrayList<>();
    private Instant savedAt;

    public List<PendingTarget> getPendingQueue() { return pendingQueue; }
    public void setPendingQueue(List<PendingTarget> pendingQueue) { this.pendingQueue = pendingQueue; }

    public Instant getSavedAt() { return savedAt; }
    public void setSavedAt(Instant savedAt) { this.savedAt = savedAt; }

    // ── Nested target ──────────────────────────────────────────────────────

    /** A URL that has been discovered but not yet fetched. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PendingTarget {
        private String url;
        private int    depth;

        public PendingTarget() {}
        public PendingTarget(String url, int depth) { this.url = url; this.depth = depth; }

        public String getUrl()   { return url; }
        public void   setUrl(String url) { this.url = url; }
        public int    getDepth() { return depth; }
        public void   setDepth(int depth) { this.depth = depth; }
    }
}
