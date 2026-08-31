package com.naydiko.backend.geometry.model;

import java.util.UUID;

/**
 * A single finding produced by the Geometry Engine: which rule was violated
 * ({@link #code()}), how serious it is ({@link #severity()}), a human
 * readable explanation, and the id of the entity it concerns (a wall,
 * opening, room, or furniture placement), when applicable.
 */
public record GeometryValidationIssue(
        GeometrySeverity severity,
        GeometryIssueCode code,
        String message,
        UUID relatedEntityId
) {

    public static GeometryValidationIssue error(GeometryIssueCode code, String message, UUID relatedEntityId) {
        return new GeometryValidationIssue(GeometrySeverity.ERROR, code, message, relatedEntityId);
    }

    public static GeometryValidationIssue warning(GeometryIssueCode code, String message, UUID relatedEntityId) {
        return new GeometryValidationIssue(GeometrySeverity.WARNING, code, message, relatedEntityId);
    }
}

