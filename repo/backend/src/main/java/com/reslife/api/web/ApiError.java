package com.reslife.api.web;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/**
 * Standard error response envelope.
 *
 * <p>{@code fieldErrors} is only present for validation failures (HTTP 400).
 * All other error responses omit it to keep the surface small.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(
        int status,
        String error,
        String message,
        Instant timestamp,
        List<FieldError> fieldErrors
) {
    public static ApiError of(int status, String error, String message) {
        return new ApiError(status, error, message, Instant.now(), null);
    }

    public static ApiError withFieldErrors(int status, String error, String message,
                                           List<FieldError> fieldErrors) {
        return new ApiError(status, error, message, Instant.now(), fieldErrors);
    }

    public record FieldError(String field, String message) {}
}
