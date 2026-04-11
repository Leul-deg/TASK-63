package com.reslife.api.domain.crawler;

/**
 * Immutable result of one HTTP fetch attempt.
 */
public record FetchResult(
        String  url,
        int     httpStatus,
        String  body,
        String  contentHash,   // SHA-256(body), null on error
        int     contentLength,
        boolean success,
        String  errorMessage
) {
    public static FetchResult ok(String url, int status, String body, String hash) {
        return new FetchResult(url, status, body, hash, body == null ? 0 : body.length(), true, null);
    }

    public static FetchResult error(String url, int status, String message) {
        return new FetchResult(url, status, null, null, 0, false, message);
    }

    public static FetchResult networkError(String url, String message) {
        return new FetchResult(url, 0, null, null, 0, false, message);
    }
}
