package com.naydiko.backend.geometry;

import com.naydiko.backend.geometry.model.GeometryIssueCode;
import com.naydiko.backend.geometry.model.GeometryValidationIssue;
import com.naydiko.backend.geometry.model.WallGeometry;

import java.util.ArrayList;
import java.util.List;

/**
 * Calculates and validates the basic geometry of a single {@link WallGeometry}:
 * length, angle, bounding box, and Stage 1 structural validity.
 */
public final class WallGeometryCalculator {

    private static final double EPSILON_MM = 0.01;

    private WallGeometryCalculator() {
    }

    /**
     * Validates a wall's structural geometry:
     * <ul>
     *     <li>start and end nodes must be different</li>
     *     <li>the wall must have positive length</li>
     *     <li>thickness must be positive</li>
     *     <li>height must be positive</li>
     * </ul>
     */
    public static List<GeometryValidationIssue> validate(WallGeometry wall) {
        List<GeometryValidationIssue> issues = new ArrayList<>();

        if (wall.startNodeId() != null && wall.startNodeId().equals(wall.endNodeId())) {
            issues.add(GeometryValidationIssue.error(
                    GeometryIssueCode.WALL_INVALID_NODES,
                    "Wall start and end nodes must be different",
                    wall.id()));
        }

        if (wall.lengthMm() <= EPSILON_MM) {
            issues.add(GeometryValidationIssue.error(
                    GeometryIssueCode.WALL_ZERO_LENGTH,
                    "Wall has zero (or near-zero) length",
                    wall.id()));
        }

        if (wall.thicknessMm() <= 0) {
            issues.add(GeometryValidationIssue.error(
                    GeometryIssueCode.WALL_NON_POSITIVE_THICKNESS,
                    "Wall thickness must be positive",
                    wall.id()));
        }

        if (wall.heightMm() <= 0) {
            issues.add(GeometryValidationIssue.error(
                    GeometryIssueCode.WALL_NON_POSITIVE_HEIGHT,
                    "Wall height must be positive",
                    wall.id()));
        }

        return issues;
    }
}

