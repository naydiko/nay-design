package com.naydiko.backend.geometry.model;

/**
 * Severity of a single {@link GeometryValidationIssue}.
 *
 * <p>{@code ERROR} indicates geometry that is structurally invalid (e.g. a
 * zero-length wall) and should block persistence. {@code WARNING} indicates
 * a spatial concern (e.g. furniture overlapping) that is useful feedback but
 * does not, by itself, make the submitted geometry unusable. {@code INFO} is
 * reserved for purely informational findings.
 */
public enum GeometrySeverity {
    ERROR,
    WARNING,
    INFO
}

