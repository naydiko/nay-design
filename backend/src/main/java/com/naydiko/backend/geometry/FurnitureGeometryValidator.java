package com.naydiko.backend.geometry;

import com.naydiko.backend.geometry.model.BoundingBox2D;
import com.naydiko.backend.geometry.model.FurnitureGeometry;
import com.naydiko.backend.geometry.model.GeometryIssueCode;
import com.naydiko.backend.geometry.model.GeometryValidationIssue;
import com.naydiko.backend.geometry.model.OrientedRectangle;
import com.naydiko.backend.geometry.model.WallGeometry;

import java.util.ArrayList;
import java.util.List;

/**
 * Validates furniture placements: whether an item fits within its room's
 * bounding box, and whether it intersects walls or other furniture.
 *
 * <p>All findings here are {@link com.naydiko.backend.geometry.model.GeometrySeverity#WARNING}
 * level: Stage 1 does not implement automatic collision prevention (the
 * canvas allows free placement), only detection/reporting.
 */
public final class FurnitureGeometryValidator {

    private static final double EPSILON_MM = 0.5;

    private FurnitureGeometryValidator() {
    }

    /** Raises {@link GeometryIssueCode#FURNITURE_MISSING_DIMENSIONS} if the product has no usable footprint. */
    public static List<GeometryValidationIssue> validateHasDimensions(
            java.util.UUID placementId, Double widthMm, Double depthMm) {
        List<GeometryValidationIssue> issues = new ArrayList<>();
        if (widthMm == null || depthMm == null || widthMm <= 0 || depthMm <= 0) {
            issues.add(GeometryValidationIssue.warning(
                    GeometryIssueCode.FURNITURE_MISSING_DIMENSIONS,
                    "Product dimensions are missing or invalid; cannot validate this placement's footprint",
                    placementId));
        }
        return issues;
    }

    /** Whether the furniture's footprint fits entirely within the room's bounding box. */
    public static List<GeometryValidationIssue> validateFitsInRoom(FurnitureGeometry furniture, BoundingBox2D roomBoundingBox) {
        List<GeometryValidationIssue> issues = new ArrayList<>();
        if (roomBoundingBox == null) {
            // No known room boundary to validate against yet (e.g. room has no walls).
            return issues;
        }
        for (double[] corner : furniture.footprint().corners()) {
            if (!roomBoundingBox.contains(corner[0], corner[1], EPSILON_MM)) {
                issues.add(GeometryValidationIssue.warning(
                        GeometryIssueCode.FURNITURE_OUTSIDE_ROOM,
                        "Furniture placement extends outside the room's bounding box",
                        furniture.id()));
                break;
            }
        }
        return issues;
    }

    /** Detects intersection between the furniture's footprint and any of the room's walls. */
    public static List<GeometryValidationIssue> validateWallIntersections(
            FurnitureGeometry furniture, List<WallGeometry> walls) {
        List<GeometryValidationIssue> issues = new ArrayList<>();
        OrientedRectangle footprint = furniture.footprint();
        for (WallGeometry wall : walls) {
            if (footprint.intersects(wall.asRectangle())) {
                issues.add(GeometryValidationIssue.warning(
                        GeometryIssueCode.FURNITURE_INTERSECTS_WALL,
                        "Furniture placement intersects wall " + wall.id(),
                        furniture.id()));
            }
        }
        return issues;
    }

    /** Detects pairwise intersections between all the given furniture items. */
    public static List<GeometryValidationIssue> validatePairwiseIntersections(List<FurnitureGeometry> items) {
        List<GeometryValidationIssue> issues = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            OrientedRectangle a = items.get(i).footprint();
            for (int j = i + 1; j < items.size(); j++) {
                OrientedRectangle b = items.get(j).footprint();
                if (a.intersects(b)) {
                    issues.add(GeometryValidationIssue.warning(
                            GeometryIssueCode.FURNITURE_INTERSECTS_FURNITURE,
                            "Furniture placement intersects placement " + items.get(j).id(),
                            items.get(i).id()));
                }
            }
        }
        return issues;
    }
}

