package com.naydiko.backend.geometry;

import com.naydiko.backend.geometry.model.GeometryIssueCode;
import com.naydiko.backend.geometry.model.GeometryValidationIssue;
import com.naydiko.backend.geometry.model.OpeningGeometry;

import java.util.ArrayList;
import java.util.List;

/**
 * Validates an {@link OpeningGeometry} (door/window/archway) against the
 * wall it is carved into.
 */
public final class OpeningGeometryValidator {

    private static final double EPSILON_MM = 0.01;

    private OpeningGeometryValidator() {
    }

    /**
     * Validates a single opening.
     *
     * @param opening      the opening to validate
     * @param wallLengthMm the length of the referenced wall, or {@code null}
     *                     if the wall could not be resolved (raises
     *                     {@link GeometryIssueCode#OPENING_MISSING_WALL})
     */
    public static List<GeometryValidationIssue> validate(OpeningGeometry opening, Double wallLengthMm) {
        List<GeometryValidationIssue> issues = new ArrayList<>();

        if (wallLengthMm == null) {
            issues.add(GeometryValidationIssue.error(
                    GeometryIssueCode.OPENING_MISSING_WALL,
                    "Opening references a wall that does not exist",
                    opening.id()));
            // Nothing further can be validated without a wall length.
            return issues;
        }

        if (opening.widthMm() <= 0) {
            issues.add(GeometryValidationIssue.error(
                    GeometryIssueCode.OPENING_NON_POSITIVE_WIDTH,
                    "Opening width must be positive",
                    opening.id()));
        }

        if (opening.heightMm() <= 0) {
            issues.add(GeometryValidationIssue.error(
                    GeometryIssueCode.OPENING_NON_POSITIVE_HEIGHT,
                    "Opening height must be positive",
                    opening.id()));
        }

        if (opening.offsetFromStartMm() < 0) {
            issues.add(GeometryValidationIssue.error(
                    GeometryIssueCode.OPENING_INVALID_OFFSET,
                    "Opening offset from wall start must not be negative",
                    opening.id()));
        }

        // offset_from_start + width <= wall_length
        if (opening.offsetFromStartMm() + opening.widthMm() > wallLengthMm + EPSILON_MM) {
            issues.add(GeometryValidationIssue.error(
                    GeometryIssueCode.OPENING_OUT_OF_BOUNDS,
                    "Opening does not fit within the wall's length (offset + width > wall length)",
                    opening.id()));
        }

        return issues;
    }
}

