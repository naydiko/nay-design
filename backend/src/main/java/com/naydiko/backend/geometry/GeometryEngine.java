package com.naydiko.backend.geometry;

import com.naydiko.backend.geometry.model.BoundingBox2D;
import com.naydiko.backend.geometry.model.FurnitureGeometry;
import com.naydiko.backend.geometry.model.GeometryValidationIssue;
import com.naydiko.backend.geometry.model.GeometryValidationResult;
import com.naydiko.backend.geometry.model.OpeningGeometry;
import com.naydiko.backend.geometry.model.RoomBoundary;
import com.naydiko.backend.geometry.model.RoomGeometryAnalysis;
import com.naydiko.backend.geometry.model.WallGeometry;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Facade over the Stage 1 Geometry Engine: basic geometric validation and
 * calculation for a level's walls, openings, rooms, and the furniture
 * placed within them.
 *
 * <p>This class is a plain domain/service component: it has no knowledge of
 * HTTP, persistence, or authentication. It operates purely on the
 * entity-agnostic {@code com.naydiko.backend.geometry.model} value types, so
 * it can be exercised with plain unit tests and reused from any part of the
 * application service layer (e.g. {@code LevelGeometryService},
 * {@code RoomService}).
 *
 * <p>Individual rules live in small, focused calculators/validators
 * ({@link WallGeometryCalculator}, {@link OpeningGeometryValidator},
 * {@link RoomGeometryCalculator}, {@link FurnitureGeometryValidator},
 * {@link DoorClearanceValidator}) so additional Stage 2+ rules can be added
 * without growing this facade unboundedly.
 */
@Component
public class GeometryEngine {

    /** Validates a batch of walls' structural geometry (see {@link WallGeometryCalculator#validate}). */
    public GeometryValidationResult validateWalls(List<WallGeometry> walls) {
        List<GeometryValidationIssue> issues = new ArrayList<>();
        for (WallGeometry wall : walls) {
            issues.addAll(WallGeometryCalculator.validate(wall));
        }
        return GeometryValidationResult.of(issues);
    }

    /**
     * Validates a batch of openings against their walls' lengths.
     *
     * @param openings        the openings to validate
     * @param wallLengthsById the length (mm) of every known wall, keyed by wall id;
     *                        an opening referencing an id absent from this map is
     *                        reported as {@code OPENING_MISSING_WALL}
     */
    public GeometryValidationResult validateOpenings(
            List<OpeningGeometry> openings, Map<UUID, Double> wallLengthsById) {
        List<GeometryValidationIssue> issues = new ArrayList<>();
        for (OpeningGeometry opening : openings) {
            issues.addAll(OpeningGeometryValidator.validate(opening, wallLengthsById.get(opening.wallId())));
        }
        return GeometryValidationResult.of(issues);
    }

    /** Analyzes a room's boundary: closed-loop check, bounding box, and dimensions. */
    public RoomGeometryAnalysis analyzeRoom(RoomBoundary boundary) {
        return RoomGeometryCalculator.analyze(boundary);
    }

    /**
     * Validates a room's furniture: fit within the room's bounding box, wall
     * intersections, and pairwise furniture-furniture intersections. All
     * findings are warnings (Stage 1 detects, but does not prevent, overlap).
     */
    public GeometryValidationResult validateFurniture(
            List<FurnitureGeometry> furniture, BoundingBox2D roomBoundingBox, List<WallGeometry> roomWalls) {
        List<GeometryValidationIssue> issues = new ArrayList<>();
        for (FurnitureGeometry item : furniture) {
            issues.addAll(FurnitureGeometryValidator.validateFitsInRoom(item, roomBoundingBox));
            issues.addAll(FurnitureGeometryValidator.validateWallIntersections(item, roomWalls));
        }
        issues.addAll(FurnitureGeometryValidator.validatePairwiseIntersections(furniture));
        return GeometryValidationResult.of(issues);
    }

    /**
     * Validates that furniture does not block the clearance area required by
     * the room's doors.
     *
     * @param doors            openings of type {@code DOOR} to check
     * @param wallsById        the wall each door belongs to, keyed by wall id
     * @param furniture        the room's furniture
     * @param clearanceDepthMm configurable clearance depth to project outward
     *                         from each door (e.g. from application config)
     */
    public GeometryValidationResult validateDoorClearances(
            List<OpeningGeometry> doors,
            Map<UUID, WallGeometry> wallsById,
            List<FurnitureGeometry> furniture,
            double clearanceDepthMm) {
        List<GeometryValidationIssue> issues = new ArrayList<>();
        for (OpeningGeometry door : doors) {
            WallGeometry wall = wallsById.get(door.wallId());
            if (wall == null) {
                continue;
            }
            issues.addAll(DoorClearanceValidator.validate(door, wall, furniture, clearanceDepthMm));
        }
        return GeometryValidationResult.of(issues);
    }
}

