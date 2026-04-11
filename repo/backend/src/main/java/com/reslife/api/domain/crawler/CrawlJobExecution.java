package com.reslife.api.domain.crawler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;

import java.net.URI;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Runnable that executes one crawl job from start (or checkpoint) to finish.
 *
 * <p>Not a Spring bean — constructed by {@link CrawlEngineService} with all
 * dependencies injected via constructor.  Spring Data repository calls each open
 * their own transaction, so this class has no `@Transactional` annotation.
 *
 * <h3>Lifecycle</h3>
 * <ol>
 *   <li>Mark job RUNNING in DB.</li>
 *   <li>Load pending queue from checkpoint (or start with {@code baseUrl}).</li>
 *   <li>Reconstruct visited set from {@code crawl_pages} rows already in DB.</li>
 *   <li>Crawl loop: throttle → fetch → incremental check → extract → discover links.</li>
 *   <li>Save checkpoint every {@link #checkpointInterval} pages.</li>
 *   <li>On pause/cancel signal: save checkpoint and return early.</li>
 *   <li>Mark final status (COMPLETED / PAUSED / CANCELLED / FAILED).</li>
 * </ol>
 */
class CrawlJobExecution implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(CrawlJobExecution.class);

    // href pattern — captures relative and absolute URLs, skips fragment-only and js: links
    private static final Pattern HREF = Pattern.compile(
            "href=[\"']([^\"'#][^\"']*)[\"']", Pattern.CASE_INSENSITIVE);

    // ── Dependencies ───────────────────────────────────────────────────────

    private final UUID                jobId;
    private final CrawlSource         source;
    private final CrawlConfig         config;
    private final CrawlJobRepository    jobRepo;
    private final CrawlPageRepository   pageRepo;
    private final CrawlItemRepository   itemRepo;
    private final CrawlSourceRepository sourceRepo;
    private final CrawlFetcherService   fetcher;
    private final ObjectMapper          objectMapper;
    private final int                   checkpointInterval;

    // ── Control signals ────────────────────────────────────────────────────

    final AtomicBoolean pauseRequested  = new AtomicBoolean(false);
    final AtomicBoolean cancelRequested = new AtomicBoolean(false);

    // ── Per-execution throttle state ───────────────────────────────────────

    private long lastFetchTimeMs = 0;

    // ── Constructor ────────────────────────────────────────────────────────

    CrawlJobExecution(UUID jobId, CrawlSource source, CrawlConfig config,
                      CrawlJobRepository jobRepo, CrawlPageRepository pageRepo,
                      CrawlItemRepository itemRepo, CrawlSourceRepository sourceRepo,
                      CrawlFetcherService fetcher,
                      ObjectMapper objectMapper, int checkpointInterval) {
        this.jobId              = jobId;
        this.source             = source;
        this.config             = config;
        this.jobRepo            = jobRepo;
        this.pageRepo           = pageRepo;
        this.itemRepo           = itemRepo;
        this.sourceRepo         = sourceRepo;
        this.fetcher            = fetcher;
        this.objectMapper       = objectMapper;
        this.checkpointInterval = checkpointInterval;
    }

    void requestPause()  { pauseRequested.set(true); }
    void requestCancel() { cancelRequested.set(true); }

    // ── Main run ───────────────────────────────────────────────────────────

    @Override
    public void run() {
        log.info("Job {} starting — source '{}' ({})", jobId, source.getName(), source.getBaseUrl());
        try {
            markStatus(CrawlStatus.RUNNING, null);
            setStartedAt();

            Deque<CrawlCheckpoint.PendingTarget> queue  = loadQueue();
            Set<String>                          visited = loadVisitedHashes();

            crawlLoop(queue, visited);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            markStatus(CrawlStatus.FAILED, "Worker thread interrupted");
        } catch (Exception e) {
            log.error("Job {} failed: {}", jobId, e.getMessage(), e);
            markStatus(CrawlStatus.FAILED, truncate(e.getMessage(), 1000));
        }
    }

    // ── Crawl loop ─────────────────────────────────────────────────────────

    private void crawlLoop(Deque<CrawlCheckpoint.PendingTarget> queue,
                           Set<String> visited) throws InterruptedException {
        int count = 0;

        while (!queue.isEmpty()) {
            if (cancelRequested.get()) {
                saveCheckpoint(queue);
                markStatus(CrawlStatus.CANCELLED, null);
                return;
            }
            if (pauseRequested.get()) {
                saveCheckpoint(queue);
                markStatus(CrawlStatus.PAUSED, null);
                return;
            }

            CrawlCheckpoint.PendingTarget target = queue.poll();
            String urlHash = CrawlFetcherService.sha256(target.getUrl());

            if (visited.contains(urlHash)) continue;
            visited.add(urlHash);

            // Per-source throttle
            enforceThrottle();

            // Fetch
            FetchResult result = fetcher.fetch(target.getUrl());
            count++;

            processResult(target, urlHash, result, queue, visited);

            // Periodic checkpoint
            if (count % checkpointInterval == 0) saveCheckpoint(queue);

            // Respect max-pages limit
            if (count >= source.getMaxPages()) {
                log.info("Job {} reached max-pages limit ({})", jobId, source.getMaxPages());
                break;
            }
        }

        saveCheckpoint(new ArrayDeque<>());
        markStatus(CrawlStatus.COMPLETED, null);
        updateSourceLastCrawled();
        log.info("Job {} completed — {} pages processed", jobId, count);
    }

    // ── Page processing ────────────────────────────────────────────────────

    private void processResult(CrawlCheckpoint.PendingTarget target,
                                String urlHash, FetchResult result,
                                Deque<CrawlCheckpoint.PendingTarget> queue,
                                Set<String> visited) {

        UUID pageId = insertPendingPage(target.getUrl(), urlHash, target.getDepth());

        if (!result.success()) {
            pageRepo.updateFetchResult(pageId, PageStatus.ERROR.name(),
                    result.httpStatus(), null, null, truncate(result.errorMessage(), 500));
            jobRepo.incrementPagesFailed(jobId);
            return;
        }

        // Incremental update check — skip if content hash unchanged since last crawl
        if (contentUnchanged(target.getUrl(), result.contentHash())) {
            pageRepo.updateFetchResult(pageId, PageStatus.SKIPPED.name(),
                    result.httpStatus(), result.contentHash(),
                    result.contentLength(), null);
            jobRepo.incrementPagesSkipped(jobId);
            return;
        }

        pageRepo.updateFetchResult(pageId, PageStatus.FETCHED.name(),
                result.httpStatus(), result.contentHash(),
                result.contentLength(), null);
        jobRepo.incrementPagesCrawled(jobId);

        extractAndSaveItems(pageId, result);
        discoverLinks(result, target.getDepth(), queue, visited);
    }

    // ── Item extraction ────────────────────────────────────────────────────

    private void extractAndSaveItems(UUID pageId, FetchResult result) {
        if (result.body() == null || result.body().isBlank()) return;

        // Keyword filter — if keywords are configured, only save items that match
        List<String> keywords = config.getKeywords();
        if (!keywords.isEmpty()) {
            String bodyLower = result.body().toLowerCase();
            boolean matches = keywords.stream().anyMatch(kw -> bodyLower.contains(kw.toLowerCase()));
            if (!matches) return;
        }

        String itemSel = config.getItemSelector();
        if (itemSel != null && !itemSel.isBlank()) {
            // Structured extraction: itemSelector is interpreted as a JSON Pointer
            // (RFC 6901, e.g. "/results" or "/data/items") when the response body is
            // valid JSON. titleSelector and linkSelector are also JSON Pointers within
            // each element.
            //
            // CSS selector / XPath support for HTML responses is NOT implemented here —
            // it requires a DOM parser (e.g. Jsoup) that is not in the current dependency
            // stack. When itemSelector is set but the body is not valid JSON, or the
            // selector does not start with '/' (i.e. is not a JSON Pointer), extraction
            // falls back to saving the full body as a single item.
            if (tryExtractFromJson(pageId, result, itemSel)) {
                return;
            }
            log.debug("Job {} — itemSelector '{}' set but body is not JSON or selector is not a " +
                      "JSON Pointer; falling back to full-body mode for {}", jobId, itemSel, result.url());
        }

        // Default / fallback: wrap the full body as a single item
        saveItem(pageId, result.url(), result.body(), null, null);
    }

    /**
     * Attempts to extract multiple items from a JSON response body using JSON Pointer selectors.
     *
     * <p>Returns {@code true} if the body was valid JSON and extraction ran (even if zero items
     * were found or the pointer pointed to a non-array), {@code false} if the body could not
     * be parsed as JSON so the caller can fall back to full-body mode.
     */
    private boolean tryExtractFromJson(UUID pageId, FetchResult result, String itemSel) {
        JsonNode root;
        try {
            root = objectMapper.readTree(result.body());
        } catch (JsonProcessingException e) {
            return false; // not JSON — caller should fall back
        }

        if (!itemSel.startsWith("/")) {
            // Not a JSON Pointer — likely a CSS selector intended for HTML.
            // CSS evaluation is not supported without a DOM parser.
            log.debug("Job {} — itemSelector '{}' does not start with '/' so it cannot be " +
                      "evaluated as a JSON Pointer; CSS selectors require a DOM parser " +
                      "(e.g. Jsoup) not currently in the dependency stack", jobId, itemSel);
            return false;
        }

        JsonNode items = root.at(itemSel);
        if (!items.isArray()) {
            // Pointer resolved but not to an array; save the pointed-to node as one item
            String body = items.isMissingNode() ? result.body() : items.toString();
            saveItem(pageId, result.url(), body, null, null);
            return true;
        }

        String titleSel = config.getTitleSelector();
        String linkSel  = config.getLinkSelector();

        for (JsonNode node : items) {
            String title   = extractJsonPointerField(node, titleSel);
            String link    = extractJsonPointerField(node, linkSel);
            String itemUrl = (link != null && !link.isBlank()) ? link : result.url();
            String body;
            try {
                body = objectMapper.writeValueAsString(node);
            } catch (JsonProcessingException e) {
                body = node.toString();
            }
            saveItem(pageId, itemUrl, body, title, link);
        }
        return true;
    }

    /**
     * Extracts a text value from a JSON node using a JSON Pointer (RFC 6901) selector.
     * Returns {@code null} if the selector is blank, not a pointer (doesn't start with '/'),
     * or the node is missing/null.
     */
    private String extractJsonPointerField(JsonNode node, String selector) {
        if (selector == null || selector.isBlank() || !selector.startsWith("/")) return null;
        JsonNode found = node.at(selector);
        return (found.isMissingNode() || found.isNull()) ? null : found.asText();
    }

    /**
     * Saves one {@link CrawlItem}, deduplicating by SHA-256 content hash within this source.
     * {@code title} and {@code link} are included in the JSON envelope when non-null, so that
     * consumers can read structured fields without re-parsing the full body.
     */
    private void saveItem(UUID pageId, String url, String body, String title, String link) {
        try {
            // LinkedHashMap preserves insertion order in the serialized JSON
            Map<String, Object> dataMap = new LinkedHashMap<>();
            dataMap.put("url",       url);
            if (title != null) dataMap.put("title", title);
            if (link  != null) dataMap.put("link",  link);
            dataMap.put("body",      body);
            dataMap.put("sourceId",  source.getId().toString());
            dataMap.put("crawledAt", Instant.now().toString());

            String rawData  = objectMapper.writeValueAsString(dataMap);
            String dataHash = CrawlFetcherService.sha256(rawData);

            boolean isNew = itemRepo.findFirstBySourceIdAndDataHash(source.getId(), dataHash).isEmpty();

            CrawlItem item = new CrawlItem();
            item.setSource(source);
            item.setJob(loadJobRef());
            item.setPage(loadPageRef(pageId));
            item.setUrl(url);
            item.setDataHash(dataHash);
            item.setRawData(rawData);
            item.setNew(isNew);
            itemRepo.save(item);
            jobRepo.incrementItemsFound(jobId);

        } catch (JsonProcessingException e) {
            log.warn("Job {} — could not serialize item for {}: {}", jobId, url, e.getMessage());
        }
    }

    // ── Link discovery ─────────────────────────────────────────────────────

    private void discoverLinks(FetchResult result, int currentDepth,
                                Deque<CrawlCheckpoint.PendingTarget> queue,
                                Set<String> visited) {

        if (result.body() == null) return;

        List<String> links = extractHrefs(result.body(), result.url());
        String paginationPattern = config.getNextPageUrlPattern();
        boolean hasPagination = paginationPattern != null && !paginationPattern.isBlank();

        for (String link : links) {
            String hash = CrawlFetcherService.sha256(link);
            if (visited.contains(hash)) continue;
            if (!isAllowed(link)) continue;

            // Pagination links bypass followLinks and maxDepth — they are sequential
            // continuations of the same page set (e.g. ?page=2), not hierarchical
            // child pages.  They are queued at the current depth so that further
            // pagination from the next page is also discovered.
            if (hasPagination && link.matches(paginationPattern)) {
                queue.offer(new CrawlCheckpoint.PendingTarget(link, currentDepth));
                continue;
            }

            // Regular links respect followLinks and the source depth limit
            if (!config.isFollowLinks()) continue;
            if (currentDepth >= source.getMaxDepth()) continue;
            queue.offer(new CrawlCheckpoint.PendingTarget(link, currentDepth + 1));
        }
    }

    private List<String> extractHrefs(String body, String baseUrl) {
        List<String> result = new ArrayList<>();
        Matcher m = HREF.matcher(body);
        while (m.find()) {
            try {
                String href = m.group(1).trim();
                if (href.startsWith("javascript:") || href.startsWith("mailto:")) continue;
                URI resolved = URI.create(baseUrl).resolve(href);
                String url = resolved.toASCIIString();
                // Strip fragment
                int frag = url.indexOf('#');
                if (frag > 0) url = url.substring(0, frag);
                result.add(url);
            } catch (Exception ignored) {}
        }
        return result.stream().distinct().toList();
    }

    private boolean isAllowed(String url) {
        List<String> blocked = config.getBlockedPathPatterns();
        if (!blocked.isEmpty() && blocked.stream().anyMatch(url::matches)) return false;
        List<String> allowed = config.getAllowedPathPatterns();
        if (!allowed.isEmpty()) return allowed.stream().anyMatch(url::matches);
        return true;
    }

    // ── DB helpers ─────────────────────────────────────────────────────────

    private UUID insertPendingPage(String url, String urlHash, int depth) {
        CrawlPage page = new CrawlPage();
        page.setJob(loadJobRef());
        page.setUrl(url);
        page.setUrlHash(urlHash);
        page.setStatus(PageStatus.PENDING);
        page.setDepth(depth);
        pageRepo.save(page);
        return page.getId();
    }

    private boolean contentUnchanged(String url, String newHash) {
        if (newHash == null) return false;
        List<String> hashes = pageRepo.findRecentContentHashesForUrl(
                source.getId(), url, PageRequest.of(0, 1));
        return !hashes.isEmpty() && newHash.equals(hashes.get(0));
    }

    private void markStatus(CrawlStatus status, String error) {
        jobRepo.findById(jobId).ifPresent(job -> {
            job.setStatus(status);
            if (error != null)               job.setErrorMessage(error);
            if (status == CrawlStatus.PAUSED)   job.setPausedAt(Instant.now());
            if (status.isTerminal())         job.setFinishedAt(Instant.now());
            jobRepo.save(job);
        });
    }

    private void setStartedAt() {
        jobRepo.findById(jobId).ifPresent(job -> {
            if (job.getStartedAt() == null) {
                job.setStartedAt(Instant.now());
                jobRepo.save(job);
            }
        });
    }

    private void updateSourceLastCrawled() {
        try {
            sourceRepo.updateLastCrawledAt(source.getId());
        } catch (Exception e) {
            log.warn("Could not update lastCrawledAt for source {}", source.getId());
        }
    }

    private void saveCheckpoint(Deque<CrawlCheckpoint.PendingTarget> queue) {
        try {
            CrawlCheckpoint cp = new CrawlCheckpoint();
            cp.setPendingQueue(new ArrayList<>(queue));
            cp.setSavedAt(Instant.now());
            String json = objectMapper.writeValueAsString(cp);
            jobRepo.updateCheckpoint(jobId, json);
        } catch (JsonProcessingException e) {
            log.warn("Job {} — could not serialize checkpoint: {}", jobId, e.getMessage());
        }
    }

    private Deque<CrawlCheckpoint.PendingTarget> loadQueue() {
        CrawlJob job = jobRepo.findById(jobId).orElseThrow();
        if (job.getCheckpoint() != null) {
            try {
                CrawlCheckpoint cp = objectMapper.readValue(job.getCheckpoint(), CrawlCheckpoint.class);
                if (cp.getPendingQueue() != null && !cp.getPendingQueue().isEmpty()) {
                    log.info("Job {} resuming from checkpoint ({} pending URLs)",
                            jobId, cp.getPendingQueue().size());
                    return new ArrayDeque<>(cp.getPendingQueue());
                }
            } catch (JsonProcessingException e) {
                log.warn("Job {} — invalid checkpoint JSON, starting fresh", jobId);
            }
        }
        Deque<CrawlCheckpoint.PendingTarget> q = new ArrayDeque<>();
        q.add(new CrawlCheckpoint.PendingTarget(source.getBaseUrl(), 0));
        return q;
    }

    private Set<String> loadVisitedHashes() {
        return new HashSet<>(pageRepo.findVisitedUrlHashesByJobId(jobId));
    }

    // Proxy references for JPA associations
    private CrawlJob loadJobRef() {
        return jobRepo.getReferenceById(jobId);
    }
    private CrawlPage loadPageRef(UUID pageId) {
        return pageRepo.getReferenceById(pageId);
    }

    // ── Throttle ───────────────────────────────────────────────────────────

    private void enforceThrottle() throws InterruptedException {
        long now     = System.currentTimeMillis();
        long elapsed = now - lastFetchTimeMs;
        long delay   = source.getDelayMsBetweenRequests();
        if (elapsed < delay) {
            Thread.sleep(delay - elapsed);
        }
        lastFetchTimeMs = System.currentTimeMillis();
    }

    // ── Utilities ──────────────────────────────────────────────────────────

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() > max ? s.substring(0, max) : s;
    }
}
