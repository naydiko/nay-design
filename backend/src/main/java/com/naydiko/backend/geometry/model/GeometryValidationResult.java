package com.naydiko.backend.geometry.model;

import java.util.List;

/**
 * The overall outcome of a Geometry Engine validation pass: whether the
 * geometry is usable ({@link #valid()} — {@code true} iff no issue has
 * {@link GeometrySeverity#ERROR} severity) plus the complete list of issues
 * found (including non-blocking warnings), so callers can decide what to do
 * with them (reject persistence, log, or eventually surface to the frontend).
 */
public record GeometryValidationResult(boolean valid, List<GeometryValidationIssue> issues) {

    private static final GeometryValidationResult OK = new GeometryValidationResult(true, List.of());

    public static GeometryValidationResult ok() {
        return OK;
    }

    public static GeometryValidationResult of(List<GeometryValidationIssue> issues) {
        boolean valid = issues.stream().noneMatch(i -> i.severity() == GeometrySeverity.ERROR);
        return new GeometryValidationResult(valid, List.copyOf(issues));
    }

    public List<GeometryValidationIssue> errors() {
        return issues.stream().filter(i -> i.severity() == GeometrySeverity.ERROR).toList();
    }

    public List<GeometryValidationIssue> warnings() {
        return issues.stream().filter(i -> i.severity() == GeometrySeverity.WARNING).toList();
    }
}

