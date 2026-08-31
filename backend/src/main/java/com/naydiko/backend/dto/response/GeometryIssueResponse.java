package com.naydiko.backend.dto.response;

import com.naydiko.backend.geometry.model.GeometryValidationIssue;

import java.util.UUID;

/**
 * A single Geometry Engine finding, surfaced to the frontend so it can show
 * human-readable validation messages, distinguish severities, and highlight
 * the related canvas object. Mirrors {@link GeometryValidationIssue} but
 * uses plain strings for severity/code so it serializes predictably.
 */
public record GeometryIssueResponse(
        String severity,
        String code,
        String message,
        UUID relatedEntityId
) {
    public static GeometryIssueResponse from(GeometryValidationIssue issue) {
        return new GeometryIssueResponse(
                issue.severity().name(),
                issue.code().name(),
                issue.message(),
                issue.relatedEntityId());
    }
}

