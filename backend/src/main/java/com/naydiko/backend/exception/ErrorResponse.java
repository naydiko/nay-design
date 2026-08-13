package com.naydiko.backend.exception;

import java.time.Instant;
import java.util.List;

/**
 * Standard error payload returned to API clients.
 */
public record ErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        List<FieldError> fieldErrors
) {

    public static ErrorResponse of(int status, String error, String message, String path) {
        return new ErrorResponse(Instant.now(), status, error, message, path, null);
    }

    public static ErrorResponse of(int status, String error, String message, String path, List<FieldError> fieldErrors) {
        return new ErrorResponse(Instant.now(), status, error, message, path, fieldErrors);
    }

    /**
     * A single field-level validation failure.
     */
    public record FieldError(String field, String message) {
    }
}

