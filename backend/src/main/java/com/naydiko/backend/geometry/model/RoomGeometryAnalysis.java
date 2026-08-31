package com.naydiko.backend.geometry.model;

import java.util.List;

/**
 * Result of analyzing a {@link RoomBoundary}: whether it forms a closed
 * loop, its bounding box and derived dimensions (when at least one wall is
 * present), and any issues found along the way.
 */
public record RoomGeometryAnalysis(
        boolean closed,
        BoundingBox2D boundingBox,
        RoomDimensions dimensions,
        List<GeometryValidationIssue> issues
) {
}

