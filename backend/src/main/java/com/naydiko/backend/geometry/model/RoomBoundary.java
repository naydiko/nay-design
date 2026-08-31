package com.naydiko.backend.geometry.model;

import java.util.List;
import java.util.UUID;

/**
 * The set of walls that (are expected to) enclose a room, for the purposes
 * of closed-boundary detection and bounding-box/dimension calculation.
 */
public record RoomBoundary(UUID roomId, List<WallGeometry> walls) {
}

