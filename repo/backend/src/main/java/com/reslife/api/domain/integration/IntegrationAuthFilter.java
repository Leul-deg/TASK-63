package com.reslife.api.domain.integration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

/**
 * Authenticates inbound requests to {@code /api/integrations/**} using HMAC-SHA256.
 *
 * <h3>Required headers</h3>
 * <pre>
 *   X-Integration-Key-ID  — the public key identifier (e.g. rk_abc123…)
 *   X-Reslife-Timestamp   — Unix epoch seconds (replay protection: ±5 min)
 *   X-Reslife-Signature   — sha256={HMAC-SHA256 of "{timestamp}\n{body}"}
 * </pre>
 *
 * <p>On success the resolved {@link IntegrationKey} is stored as a request attribute
 * ({@link #KEY_ATTR}) for use by the controller.  On failure the filter short-circuits
 * with an appropriate HTTP status code and writes a minimal JSON error body.
 *
 * <p>This class is not a {@code @Component} — it is registered as a Spring {@code @Bean}
 * in {@link com.reslife.api.config.IntegrationConfig} and injected into the Spring Security
 * filter chain, preventing double registration as both a servlet filter and a security filter.
 */
public class IntegrationAuthFilter extends OncePerRequestFilter {

    /** Request attribute key under which the authenticated {@link IntegrationKey} is stored. */
    public static final String KEY_ATTR   = "integration.key";
    public static final String KEY_ID_HDR = "X-Integration-Key-ID";
    public static final String TS_HDR     = "X-Reslife-Timestamp";
    public static final String SIG_HDR    = "X-Reslife-Signature";

    private static final Logger          log     = LoggerFactory.getLogger(IntegrationAuthFilter.class);
    private static final AntPathMatcher  MATCHER = new AntPathMatcher();

    private final IntegrationKeyRepository     keyRepo;
    private final HmacService                  hmacService;
    private final IntegrationRateLimiter       rateLimiter;
    private final IntegrationAuditLogRepository auditRepo;
    private final ObjectMapper                 objectMapper;

    public IntegrationAuthFilter(IntegrationKeyRepository keyRepo,
                                  HmacService hmacService,
                                  IntegrationRateLimiter rateLimiter,
                                  IntegrationAuditLogRepository auditRepo,
                                  ObjectMapper objectMapper) {
        this.keyRepo     = keyRepo;
        this.hmacService = hmacService;
        this.rateLimiter = rateLimiter;
        this.auditRepo   = auditRepo;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return !MATCHER.match("/api/integrations/**", path);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain chain)
            throws ServletException, IOException {

        // Buffer body so the controller can also read it
        CachedBodyHttpServletRequest wrapped = new CachedBodyHttpServletRequest(request);
        byte[] body = wrapped.getCachedBody();

        String keyIdHeader = request.getHeader(KEY_ID_HDR);
        String tsHeader    = request.getHeader(TS_HDR);
        String sigHeader   = request.getHeader(SIG_HDR);
        String requestId   = request.getHeader("X-Request-ID");
        String sourceIp    = request.getRemoteAddr();
        String path        = request.getRequestURI().substring(request.getContextPath().length());

        if (keyIdHeader == null || tsHeader == null || sigHeader == null) {
            reject(response, HttpStatus.UNAUTHORIZED,
                    "Missing required headers: " + KEY_ID_HDR + ", " + TS_HDR + ", " + SIG_HDR);
            return;
        }

        // Key lookup
        IntegrationKey key = keyRepo.findByKeyIdAndActiveTrue(keyIdHeader).orElse(null);
        if (key == null) {
            reject(response, HttpStatus.UNAUTHORIZED, "Unknown or revoked integration key");
            return;
        }

        String inboundEventType = extractInboundEventType(path);
        if (inboundEventType != null && !isEventAllowed(key, inboundEventType)) {
            audit(key, path, sourceIp, 403, false,
                    "Event type '" + inboundEventType + "' is not allowed for this integration key", requestId);
            reject(response, HttpStatus.FORBIDDEN,
                    "Event type '" + inboundEventType + "' is not allowed for this integration key");
            return;
        }

        // Rate limit — checked before HMAC to prevent brute-force cost
        if (!rateLimiter.tryAcquire(keyIdHeader)) {
            audit(key, path, sourceIp, 429, false, "Rate limit exceeded", requestId);
            response.setHeader("Retry-After", "60");
            response.setHeader("X-RateLimit-Limit", String.valueOf(IntegrationRateLimiter.LIMIT));
            reject(response, HttpStatus.TOO_MANY_REQUESTS,
                    "Rate limit exceeded — " + IntegrationRateLimiter.LIMIT + " requests/minute");
            return;
        }

        // Timestamp parse
        long timestamp;
        try {
            timestamp = Long.parseLong(tsHeader);
        } catch (NumberFormatException e) {
            audit(key, path, sourceIp, 401, false, "Invalid timestamp format", requestId);
            reject(response, HttpStatus.UNAUTHORIZED, "Invalid " + TS_HDR + " — must be Unix epoch seconds");
            return;
        }

        // HMAC verification (also checks replay window)
        try {
            hmacService.verify(key.getSecret(), timestamp, body, sigHeader);
        } catch (InvalidSignatureException e) {
            audit(key, path, sourceIp, 401, false, e.getMessage(), requestId);
            reject(response, HttpStatus.UNAUTHORIZED, e.getMessage());
            return;
        }

        // Update last-used timestamp (best-effort)
        try {
            key.setLastUsedAt(Instant.now());
            keyRepo.save(key);
        } catch (Exception e) {
            log.warn("Could not update lastUsedAt for key {}: {}", keyIdHeader, e.getMessage());
        }

        wrapped.setAttribute(KEY_ATTR, key);
        chain.doFilter(wrapped, response);
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private String extractInboundEventType(String path) {
        String prefix = "/api/integrations/events/";
        if (!path.startsWith(prefix) || path.length() <= prefix.length()) {
            return null;
        }
        return path.substring(prefix.length());
    }

    private boolean isEventAllowed(IntegrationKey key, String eventType) {
        String allowedEvents = key.getAllowedEvents();
        if (allowedEvents == null || allowedEvents.isBlank()) {
            return true;
        }
        try {
            List<String> parsed = objectMapper.readValue(allowedEvents, new TypeReference<>() {});
            return parsed.stream().anyMatch(eventType::equals);
        } catch (Exception e) {
            log.warn("Could not parse allowedEvents for key {}: {}", key.getKeyId(), e.getMessage());
            return false;
        }
    }

    private void reject(HttpServletResponse response, HttpStatus status, String message)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        // Escape any double-quotes in the message to keep JSON valid
        response.getWriter().write("{\"error\":\"" + message.replace("\"", "'") + "\"}");
    }

    private void audit(IntegrationKey key, String path, String sourceIp,
                        int status, boolean success, String error, String requestId) {
        try {
            IntegrationAuditLog entry = new IntegrationAuditLog();
            entry.setIntegrationKey(key);
            entry.setDirection(IntegrationAuditLog.Direction.INBOUND);
            entry.setTargetUrl(path);
            entry.setSourceIp(sourceIp);
            entry.setHttpStatus(status);
            entry.setSuccess(success);
            entry.setErrorMessage(error);
            entry.setRequestId(requestId);
            auditRepo.save(entry);
        } catch (Exception e) {
            log.warn("Failed to write integration auth audit log", e);
        }
    }
}
