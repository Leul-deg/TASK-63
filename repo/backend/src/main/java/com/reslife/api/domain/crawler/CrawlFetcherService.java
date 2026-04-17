package com.reslife.api.domain.crawler;

import com.reslife.api.domain.integration.LocalNetworkValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;

/**
 * Stateless HTTP fetch service for the crawler engine.
 *
 * <p>All targets are validated against the local-network allowlist before any
 * request is made, preventing the crawler from reaching public internet hosts.
 *
 * <p>Throttle enforcement (delay between requests) is handled by the caller
 * ({@link CrawlJobExecution}) because it is stateful per execution.
 */
@Service
public class CrawlFetcherService {

    private static final Logger log = LoggerFactory.getLogger(CrawlFetcherService.class);
    private static final int MAX_REDIRECTS = 5;

    private final LocalNetworkValidator localNetworkValidator;
    private final HttpClient            httpClient;
    private final String                userAgent;

    @org.springframework.beans.factory.annotation.Autowired
    public CrawlFetcherService(LocalNetworkValidator localNetworkValidator,
                                CrawlerProperties props) {
        this(
                localNetworkValidator,
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(props.getFetchTimeoutSeconds()))
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .build(),
                props.getUserAgent()
        );
    }

    CrawlFetcherService(LocalNetworkValidator localNetworkValidator,
                        HttpClient httpClient,
                        String userAgent) {
        this.localNetworkValidator = localNetworkValidator;
        this.httpClient = httpClient;
        this.userAgent = userAgent;
    }

    /**
     * Fetches {@code url}, validates it is on the local network, and returns the result.
     * Never throws — network/parse errors are returned as {@link FetchResult#networkError}.
     */
    public FetchResult fetch(String url) {
        String currentUrl = url;
        int redirectCount = 0;
        try {
            while (true) {
                try {
                    localNetworkValidator.requireLocalTarget(currentUrl);
                } catch (IllegalArgumentException e) {
                    log.warn("Crawler blocked non-local URL {}: {}", currentUrl, e.getMessage());
                    return FetchResult.networkError(currentUrl, "Blocked: " + e.getMessage());
                }

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(currentUrl))
                        .header("User-Agent", userAgent)
                        .header("Accept", "text/html,application/xhtml+xml,application/json,*/*")
                        .timeout(Duration.ofSeconds(30))
                        .GET()
                        .build();

                HttpResponse<String> response = httpClient.send(
                        request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

                int status = response.statusCode();
                if (isRedirect(status)) {
                    String location = response.headers().firstValue("Location").orElse(null);
                    if (location == null || location.isBlank()) {
                        return FetchResult.error(currentUrl, status, "Redirect response missing Location header");
                    }
                    if (++redirectCount > MAX_REDIRECTS) {
                        return FetchResult.error(currentUrl, status, "Too many redirects");
                    }
                    currentUrl = URI.create(currentUrl).resolve(location).toString();
                    continue;
                }

                String body = response.body();
                if (status < 200 || status >= 400) {
                    return FetchResult.error(currentUrl, status, "HTTP " + status);
                }

                String hash = sha256(body == null ? "" : body);
                return FetchResult.ok(currentUrl, status, body, hash);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return FetchResult.networkError(currentUrl, "Interrupted");
        } catch (Exception e) {
            log.debug("Fetch error for {}: {}", currentUrl, e.getMessage());
            return FetchResult.networkError(currentUrl, e.getMessage());
        }
    }

    private static boolean isRedirect(int status) {
        return status == 301 || status == 302 || status == 303 || status == 307 || status == 308;
    }

    /** Computes the SHA-256 hex digest of the given string (UTF-8 encoded). */
    public static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
