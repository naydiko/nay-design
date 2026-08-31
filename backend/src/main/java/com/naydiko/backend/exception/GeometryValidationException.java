package com.naydiko.backend.exception;

import com.naydiko.backend.geometry.model.GeometryValidationIssue;

import java.util.List;

/**
 * Thrown when the {@link com.naydiko.backend.geometry.GeometryEngine} finds
 * one or more error-severity issues in geometry submitted for persistence
 * (e.g. a zero-length wall, or an opening that does not fit within its
 * wall). Carries the full list of issues found, including any non-blocking
 * warnings, so callers/handlers can report all of them at once.
 */
public class GeometryValidationException extends RuntimeException {

    private final List<GeometryValidationIssue> issues;

    public GeometryValidationException(List<GeometryValidationIssue> issues) {
        super("Geometry validation failed: " + summarize(issues));
        this.issues = issues;
    }

    public List<GeometryValidationIssue> getIssues() {
        return issues;
    }

    private static String summarize(List<GeometryValidationIssue> issues) {
        return issues.stream()
                .map(i -> i.code() + ": " + i.message())
                .reduce((a, b) -> a + "; " + b)
                .orElse("no details");
    }
}

