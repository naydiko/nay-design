package com.naydiko.backend.exception;

import com.naydiko.backend.dto.response.GeometryIssueResponse;

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
        List<FieldError> fieldErrors,
        /** Structured Geometry Engine findings (severity/code/message/relatedEntityId),
         *  populated only when the error originated from geometry validation. */
        List<GeometryIssueResponse> issues
) {

    public static ErrorResponse of(int status, String error, String message, String path) {
        return new ErrorResponse(Instant.now(), status, error, message, path, null, null);
    }

    public static ErrorResponse of(int status, String error, String message, String path, List<FieldError> fieldErrors) {
        return new ErrorResponse(Instant.now(), status, error, message, path, fieldErrors, null);
    }

    public static ErrorResponse ofGeometryIssues(
            int status, String error, String message, String path,
            List<FieldError> fieldErrors, List<GeometryIssueResponse> issues) {
        return new ErrorResponse(Instant.now(), status, error, message, path, fieldErrors, issues);
    }

    /**
     * A single field-level validation failure.
     */
    public record FieldError(String field, String message) {
    }
}

