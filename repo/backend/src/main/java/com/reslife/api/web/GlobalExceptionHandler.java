package com.reslife.api.web;

import com.reslife.api.domain.integration.InvalidSignatureException;
import com.reslife.api.domain.integration.RateLimitExceededException;
import com.reslife.api.domain.messaging.BlockedException;
import com.reslife.api.domain.resident.DuplicateCheckResponse;
import com.reslife.api.domain.resident.DuplicateResidentException;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.List;

/**
 * Translates exceptions into consistent {@link ApiError} JSON responses.
 *
 * <p>Auth-related Spring Security exceptions ({@code AuthenticationException},
 * {@code AccessDeniedException}) are NOT handled here — they never reach a
 * {@code @ExceptionHandler} because Spring Security's filter chain intercepts
 * them first and the custom entry points in {@code SecurityConfig} handle them.
 *
 * <p>The only auth exception that DOES reach here is {@link BadCredentialsException}
 * thrown explicitly from {@code AuthService} within the controller call stack.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Possible duplicate residents on create — returns 409 with the candidate list
     * so the frontend can show a "save anyway?" confirmation.
     */
    @ExceptionHandler(DuplicateResidentException.class)
    public ResponseEntity<DuplicateCheckResponse> handleDuplicateResident(
            DuplicateResidentException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ex.getDuplicates());
    }

    /** Recipient has blocked the sender from starting new threads. */
    @ExceptionHandler(BlockedException.class)
    public ResponseEntity<ApiError> handleBlocked(BlockedException ex) {
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(ApiError.of(403, "Forbidden", ex.getMessage()));
    }

    /** Login failures — always the same generic message regardless of reason. */
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiError> handleBadCredentials(BadCredentialsException ex) {
        // Never echo ex.getMessage() — it may contain implementation details.
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ApiError.of(401, "Unauthorized", "Invalid credentials"));
    }

    /** Method-security access denial that reaches MVC rather than the filter chain. */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(ApiError.of(403, "Forbidden", "Access denied"));
    }

    /**
     * Multipart file exceeds Spring's configured limit (thrown before reaching our code).
     * Returns 413 so the browser/client gets a meaningful status.
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> handleMaxUploadSize(MaxUploadSizeExceededException ex) {
        return ResponseEntity
                .status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(ApiError.of(413, "Payload Too Large",
                        "File exceeds the maximum allowed size of 15 MB"));
    }

    /** Resource not found. */
    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(EntityNotFoundException ex) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiError.of(404, "Not Found", ex.getMessage()));
    }

    /** Constraint violations from request body validation (@Valid). */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        List<ApiError.FieldError> fieldErrors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(fe -> new ApiError.FieldError(fe.getField(), fe.getDefaultMessage()))
                .toList();

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiError.withFieldErrors(400, "Bad Request",
                        "Validation failed", fieldErrors));
    }

    /**
     * HMAC signature failure or stale timestamp on integration requests.
     * Note: for requests that reach the filter these are handled there directly;
     * this handler covers any edge case where they propagate to Spring MVC.
     */
    @ExceptionHandler(InvalidSignatureException.class)
    public ResponseEntity<ApiError> handleInvalidSignature(InvalidSignatureException ex) {
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ApiError.of(401, "Unauthorized", ex.getMessage()));
    }

    /** Integration key has exceeded its per-minute request quota. */
    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ApiError> handleRateLimit(RateLimitExceededException ex) {
        return ResponseEntity
                .status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", "60")
                .body(ApiError.of(429, "Too Many Requests", ex.getMessage()));
    }

    /** Illegal arguments from service layer (e.g. admin trying to change own status). */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity
                .status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ApiError.of(422, "Unprocessable Entity", ex.getMessage()));
    }

    /** Catch-all — log the full stack trace server-side but return a safe message. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneric(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiError.of(500, "Internal Server Error",
                        "An unexpected error occurred"));
    }
}
