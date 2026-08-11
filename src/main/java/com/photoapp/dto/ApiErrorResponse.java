package com.photoapp.dto;

import java.time.Instant;

/**
 * Uniform error envelope returned by {@code GlobalExceptionHandler}.
 */
public record ApiErrorResponse(Object data, String error, Instant timestamp) {

    public static ApiErrorResponse of(String message) {
        return new ApiErrorResponse(null, message, Instant.now());
    }
}
