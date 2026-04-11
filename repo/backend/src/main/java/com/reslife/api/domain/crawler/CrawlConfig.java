package com.reslife.api.domain.crawler;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Per-source crawl configuration, stored as JSON in {@code crawl_sources.crawl_config}.
 *
 * <p>Controls pagination discovery, link following, URL filtering, and item extraction.
 * All fields have safe defaults so an empty JSON object produces a working configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class CrawlConfig {

    // ── Link discovery ─────────────────────────────────────────────────────

    /**
     * Whether to follow discovered {@code href} links within the allowed depth.
     * Set to {@code false} to crawl only the exact {@code baseUrl}.
     */
    private boolean followLinks = true;

    /**
     * Regex patterns against which discovered URLs are tested.
     * A URL must match at least one pattern to be queued (if non-empty).
     * Example: {@code ["^http://intranet\\.local/residents/.*"]}
     */
    private List<String> allowedPathPatterns = new ArrayList<>();

    /**
     * Regex patterns for URLs that should never be queued regardless of allow list.
     */
    private List<String> blockedPathPatterns = new ArrayList<>();

    // ── Pagination ─────────────────────────────────────────────────────────

    /**
     * Regex applied to hrefs to identify "next page" links, e.g. {@code ".*[?&]page=\\d+"}.
     * When present, the crawler treats matching links as pagination rather than depth links.
     */
    private String nextPageUrlPattern;

    // ── Item extraction (HTML) ─────────────────────────────────────────────

    /**
     * CSS selector or XPath identifying individual data items on the page.
     * When set, matched elements are serialized and saved as {@code CrawlItem} rows.
     * Leave blank to save the full page body as a single item.
     */
    private String itemSelector;

    /**
     * JSON pointer / CSS selector to extract a title field from each item.
     */
    private String titleSelector;

    /**
     * JSON pointer / CSS selector to extract a canonical URL from each item.
     */
    private String linkSelector;

    // ── Keyword filtering ──────────────────────────────────────────────────

    /**
     * Only save items whose text content contains at least one of these keywords.
     * Empty list = save all items.
     */
    private List<String> keywords = new ArrayList<>();

    // ── Getters / Setters ──────────────────────────────────────────────────

    public boolean isFollowLinks() { return followLinks; }
    public void setFollowLinks(boolean followLinks) { this.followLinks = followLinks; }

    public List<String> getAllowedPathPatterns() { return allowedPathPatterns; }
    public void setAllowedPathPatterns(List<String> allowedPathPatterns) { this.allowedPathPatterns = allowedPathPatterns; }

    public List<String> getBlockedPathPatterns() { return blockedPathPatterns; }
    public void setBlockedPathPatterns(List<String> blockedPathPatterns) { this.blockedPathPatterns = blockedPathPatterns; }

    public String getNextPageUrlPattern() { return nextPageUrlPattern; }
    public void setNextPageUrlPattern(String nextPageUrlPattern) { this.nextPageUrlPattern = nextPageUrlPattern; }

    public String getItemSelector() { return itemSelector; }
    public void setItemSelector(String itemSelector) { this.itemSelector = itemSelector; }

    public String getTitleSelector() { return titleSelector; }
    public void setTitleSelector(String titleSelector) { this.titleSelector = titleSelector; }

    public String getLinkSelector() { return linkSelector; }
    public void setLinkSelector(String linkSelector) { this.linkSelector = linkSelector; }

    public List<String> getKeywords() { return keywords; }
    public void setKeywords(List<String> keywords) { this.keywords = keywords; }
}
