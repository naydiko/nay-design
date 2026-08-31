package com.naydiko.backend.geometry;

import com.naydiko.backend.geometry.model.BoundingBox2D;
import com.naydiko.backend.geometry.model.GeometryIssueCode;
import com.naydiko.backend.geometry.model.GeometryValidationIssue;
import com.naydiko.backend.geometry.model.RoomBoundary;
import com.naydiko.backend.geometry.model.RoomDimensions;
import com.naydiko.backend.geometry.model.RoomGeometryAnalysis;
import com.naydiko.backend.geometry.model.WallGeometry;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Calculates a room's basic geometry (bounding box, dimensions) from the set
 * of walls that are expected to enclose it, and performs a basic check of
 * whether those walls form a closed boundary.
 *
 * <p>Stage 1 intentionally does not attempt to derive the room's true
 * (possibly non-rectangular, non-convex) polygon or area — only whether the
 * walls form a single closed loop, and the enclosing rectangle.
 */
public final class RoomGeometryCalculator {

    private RoomGeometryCalculator() {
    }

    /**
     * Determines whether the given walls form a single closed loop: every
     * node touched by exactly the given walls has degree 2 (each node is
     * shared by exactly two walls), and the walls form one connected cycle
     * (not several disjoint loops).
     */
    public static boolean isClosed(RoomBoundary boundary) {
        List<WallGeometry> walls = boundary.walls();
        if (walls.isEmpty()) {
            return false;
        }

        Map<UUID, List<UUID>> adjacency = new HashMap<>();
        for (WallGeometry wall : walls) {
            adjacency.computeIfAbsent(wall.startNodeId(), k -> new ArrayList<>()).add(wall.endNodeId());
            adjacency.computeIfAbsent(wall.endNodeId(), k -> new ArrayList<>()).add(wall.startNodeId());
        }

        // Every node must have exactly degree 2 for a simple closed loop.
        for (List<UUID> neighbors : adjacency.values()) {
            if (neighbors.size() != 2) {
                return false;
            }
        }

        // Must form a single connected component spanning all nodes/walls.
        UUID start = walls.get(0).startNodeId();
        Set<UUID> visited = new HashSet<>();
        Deque<UUID> stack = new ArrayDeque<>();
        stack.push(start);
        while (!stack.isEmpty()) {
            UUID current = stack.pop();
            if (!visited.add(current)) {
                continue;
            }
            for (UUID neighbor : adjacency.getOrDefault(current, List.of())) {
                if (!visited.contains(neighbor)) {
                    stack.push(neighbor);
                }
            }
        }

        return visited.size() == adjacency.size();
    }

    public static BoundingBox2D boundingBox(RoomBoundary boundary) {
        List<WallGeometry> walls = boundary.walls();
        if (walls.isEmpty()) {
            return null;
        }
        BoundingBox2D box = walls.get(0).boundingBox();
        for (int i = 1; i < walls.size(); i++) {
            box = box.union(walls.get(i).boundingBox());
        }
        return box;
    }

    public static RoomDimensions dimensions(BoundingBox2D boundingBox) {
        if (boundingBox == null) {
            return null;
        }
        return new RoomDimensions(boundingBox.width(), boundingBox.height());
    }

    /**
     * Runs the full Stage 1 room analysis. A non-closed boundary is reported
     * as a {@link GeometryIssueCode#ROOM_NOT_CLOSED} warning (not an error):
     * many valid in-progress rooms will not yet be fully enclosed.
     */
    public static RoomGeometryAnalysis analyze(RoomBoundary boundary) {
        boolean closed = isClosed(boundary);
        BoundingBox2D box = boundingBox(boundary);
        RoomDimensions dims = dimensions(box);

        List<GeometryValidationIssue> issues = new ArrayList<>();
        if (!boundary.walls().isEmpty() && !closed) {
            issues.add(GeometryValidationIssue.warning(
                    GeometryIssueCode.ROOM_NOT_CLOSED,
                    "The room's walls do not yet form a closed boundary",
                    boundary.roomId()));
        }

        return new RoomGeometryAnalysis(closed, box, dims, issues);
    }
}


