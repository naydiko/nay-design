package com.naydiko.backend.geometry;

import com.naydiko.backend.geometry.model.FurnitureGeometry;
import com.naydiko.backend.geometry.model.GeometryIssueCode;
import com.naydiko.backend.geometry.model.GeometryValidationResult;
import com.naydiko.backend.geometry.model.OpeningGeometry;
import com.naydiko.backend.geometry.model.RoomBoundary;
import com.naydiko.backend.geometry.model.WallGeometry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end tests of the {@link GeometryEngine} facade, exercising the
 * scenarios called out in the Stage 1 Geometry Engine requirements.
 */
class GeometryEngineTest {

    private final GeometryEngine engine = new GeometryEngine();

    @Test
    void validWallPassesValidation() {
        WallGeometry wall = new WallGeometry(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 0, 0, 4000, 0, 100, 2700);

        GeometryValidationResult result = engine.validateWalls(List.of(wall));

        assertThat(result.valid()).isTrue();
        assertThat(result.issues()).isEmpty();
    }

    @Test
    void zeroLengthWallFailsValidation() {
        UUID sameNode = UUID.randomUUID();
        WallGeometry wall = new WallGeometry(
                UUID.randomUUID(), sameNode, UUID.randomUUID(), 100, 100, 100, 100, 100, 2700);

        GeometryValidationResult result = engine.validateWalls(List.of(wall));

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).extracting(i -> i.code()).contains(GeometryIssueCode.WALL_ZERO_LENGTH);
    }

    @Test
    void openingFittingOnWallPassesValidation() {
        UUID wallId = UUID.randomUUID();
        WallGeometry wall = new WallGeometry(wallId, UUID.randomUUID(), UUID.randomUUID(), 0, 0, 4000, 0, 100, 2700);
        OpeningGeometry opening = new OpeningGeometry(UUID.randomUUID(), wallId, "DOOR", 500, 900, 2100);

        GeometryValidationResult result = engine.validateOpenings(
                List.of(opening), Map.of(wallId, wall.lengthMm()));

        assertThat(result.valid()).isTrue();
    }

    @Test
    void openingExceedingWallFailsValidation() {
        UUID wallId = UUID.randomUUID();
        WallGeometry wall = new WallGeometry(wallId, UUID.randomUUID(), UUID.randomUUID(), 0, 0, 1000, 0, 100, 2700);
        OpeningGeometry opening = new OpeningGeometry(UUID.randomUUID(), wallId, "DOOR", 500, 900, 2100);

        GeometryValidationResult result = engine.validateOpenings(
                List.of(opening), Map.of(wallId, wall.lengthMm()));

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).extracting(i -> i.code()).contains(GeometryIssueCode.OPENING_OUT_OF_BOUNDS);
    }

    @Test
    void furnitureFittingInRoomHasNoErrorsAndFitWithinBoundingBox() {
        List<WallGeometry> walls = rectangularRoomWalls();
        var analysis = engine.analyzeRoom(new RoomBoundary(UUID.randomUUID(), walls));

        FurnitureGeometry sofa = new FurnitureGeometry(
                UUID.randomUUID(), UUID.randomUUID(), 2000, 1500, 1800, 800, 0, 1, false);

        GeometryValidationResult result = engine.validateFurniture(List.of(sofa), analysis.boundingBox(), walls);

        assertThat(result.issues()).isEmpty();
    }

    @Test
    void furnitureOutsideRoomIsReported() {
        List<WallGeometry> walls = rectangularRoomWalls();
        var analysis = engine.analyzeRoom(new RoomBoundary(UUID.randomUUID(), walls));

        FurnitureGeometry outside = new FurnitureGeometry(
                UUID.randomUUID(), UUID.randomUUID(), 9000, 9000, 500, 500, 0, 1, false);

        GeometryValidationResult result = engine.validateFurniture(List.of(outside), analysis.boundingBox(), walls);

        assertThat(result.issues()).extracting(i -> i.code()).contains(GeometryIssueCode.FURNITURE_OUTSIDE_ROOM);
    }

    @Test
    void twoFurnitureItemsThatOverlapAreReported() {
        FurnitureGeometry a = new FurnitureGeometry(
                UUID.randomUUID(), UUID.randomUUID(), 1000, 1000, 700, 700, 0, 1, false);
        FurnitureGeometry b = new FurnitureGeometry(
                UUID.randomUUID(), UUID.randomUUID(), 1300, 1000, 700, 700, 0, 1, false);

        GeometryValidationResult result = engine.validateFurniture(List.of(a, b), null, List.of());

        assertThat(result.issues()).extracting(i -> i.code()).contains(GeometryIssueCode.FURNITURE_INTERSECTS_FURNITURE);
    }

    @Test
    void furnitureBlockingDoorClearanceIsReported() {
        UUID wallId = UUID.randomUUID();
        WallGeometry wall = new WallGeometry(wallId, UUID.randomUUID(), UUID.randomUUID(), 0, 0, 4000, 0, 100, 2700);
        OpeningGeometry door = new OpeningGeometry(UUID.randomUUID(), wallId, "DOOR", 1000, 900, 2100);
        FurnitureGeometry blocker = new FurnitureGeometry(
                UUID.randomUUID(), UUID.randomUUID(), 1450, 300, 600, 600, 0, 1, false);

        GeometryValidationResult result = engine.validateDoorClearances(
                List.of(door), Map.of(wallId, wall), List.of(blocker), 700);

        assertThat(result.issues()).extracting(i -> i.code()).containsExactly(GeometryIssueCode.DOOR_BLOCKED);
    }

    private List<WallGeometry> rectangularRoomWalls() {
        UUID n1 = UUID.randomUUID();
        UUID n2 = UUID.randomUUID();
        UUID n3 = UUID.randomUUID();
        UUID n4 = UUID.randomUUID();
        return List.of(
                new WallGeometry(UUID.randomUUID(), n1, n2, 0, 0, 4000, 0, 100, 2700),
                new WallGeometry(UUID.randomUUID(), n2, n3, 4000, 0, 4000, 3000, 100, 2700),
                new WallGeometry(UUID.randomUUID(), n3, n4, 4000, 3000, 0, 3000, 100, 2700),
                new WallGeometry(UUID.randomUUID(), n4, n1, 0, 3000, 0, 0, 100, 2700)
        );
    }
}


