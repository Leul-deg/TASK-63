package com.reslife.api.domain.crawler;

public enum SiteType {
    /** Standard HTML page — links are extracted from {@code href} attributes. */
    HTML,
    /** JSON REST API endpoint — links and items come from the JSON response body. */
    JSON_API,
    /** RSS or Atom feed — items are extracted from {@code <item>/<entry>} elements. */
    RSS
}
