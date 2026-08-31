package com.naydiko.backend.geometry;

import com.naydiko.backend.geometry.model.BoundingBox2D;
import com.naydiko.backend.geometry.model.FurnitureGeometry;
import com.naydiko.backend.geometry.model.GeometryIssueCode;
import com.naydiko.backend.geometry.model.RoomBoundary;
import com.naydiko.backend.geometry.model.WallGeometry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FurnitureGeometryValidatorTest {

    /** A simple 4000x3000mm rectangular room, walls 100mm thick. */
    private List<WallGeometry> rectangularRoomWalls() {
        UUID n1 = UUID.randomUUID();
        UUID n2 = UUID.randomUUID();
        UUID n3 = UUID.randomUUID();
        UUID n4 = UUID.randomUUID();
        double t = 100;
        double h = 2700;
        return List.of(
                new WallGeometry(UUID.randomUUID(), n1, n2, 0, 0, 4000, 0, t, h),       // south
                new WallGeometry(UUID.randomUUID(), n2, n3, 4000, 0, 4000, 3000, t, h), // east
                new WallGeometry(UUID.randomUUID(), n3, n4, 4000, 3000, 0, 3000, t, h), // north
                new WallGeometry(UUID.randomUUID(), n4, n1, 0, 3000, 0, 0, t, h)        // west
        );
    }

    @Test
    void furnitureThatFitsInRoomHasNoOutsideRoomIssue() {
        List<WallGeometry> walls = rectangularRoomWalls();
        BoundingBox2D roomBox = RoomGeometryCalculator.boundingBox(new RoomBoundary(UUID.randomUUID(), walls));

        FurnitureGeometry sofa = new FurnitureGeometry(
                UUID.randomUUID(), UUID.randomUUID(), 2000, 1500, 1800, 800, 0, 1, false);

        assertThat(FurnitureGeometryValidator.validateFitsInRoom(sofa, roomBox)).isEmpty();
        assertThat(FurnitureGeometryValidator.validateWallIntersections(sofa, walls)).isEmpty();
    }

    @Test
    void furnitureOutsideRoomIsFlagged() {
        List<WallGeometry> walls = rectangularRoomWalls();
        BoundingBox2D roomBox = RoomGeometryCalculator.boundingBox(new RoomBoundary(UUID.randomUUID(), walls));

        UUID furnitureId = UUID.randomUUID();
        FurnitureGeometry farAway = new FurnitureGeometry(
                furnitureId, UUID.randomUUID(), 9000, 1500, 500, 500, 0, 1, false);

        assertThat(FurnitureGeometryValidator.validateFitsInRoom(farAway, roomBox))
                .extracting(i -> i.code())
                .containsExactly(GeometryIssueCode.FURNITURE_OUTSIDE_ROOM);
    }

    @Test
    void furnitureOverlappingWallIsFlagged() {
        List<WallGeometry> walls = rectangularRoomWalls();

        // Straddles the west wall (centerline x=0), so it overlaps the wall's footprint.
        FurnitureGeometry cabinet = new FurnitureGeometry(
                UUID.randomUUID(), UUID.randomUUID(), 0, 1500, 200, 200, 0, 1, false);

        assertThat(FurnitureGeometryValidator.validateWallIntersections(cabinet, walls))
                .extracting(i -> i.code())
                .contains(GeometryIssueCode.FURNITURE_INTERSECTS_WALL);
    }

    @Test
    void twoOverlappingFurnitureItemsAreFlagged() {
        FurnitureGeometry a = new FurnitureGeometry(
                UUID.randomUUID(), UUID.randomUUID(), 1000, 1000, 600, 600, 0, 1, false);
        FurnitureGeometry b = new FurnitureGeometry(
                UUID.randomUUID(), UUID.randomUUID(), 1200, 1000, 600, 600, 0, 1, false);

        assertThat(FurnitureGeometryValidator.validatePairwiseIntersections(List.of(a, b)))
                .extracting(i -> i.code())
                .containsExactly(GeometryIssueCode.FURNITURE_INTERSECTS_FURNITURE);
    }

    @Test
    void nonOverlappingFurnitureItemsAreNotFlagged() {
        FurnitureGeometry a = new FurnitureGeometry(
                UUID.randomUUID(), UUID.randomUUID(), 500, 500, 400, 400, 0, 1, false);
        FurnitureGeometry b = new FurnitureGeometry(
                UUID.randomUUID(), UUID.randomUUID(), 3000, 2500, 400, 400, 0, 1, false);

        assertThat(FurnitureGeometryValidator.validatePairwiseIntersections(List.of(a, b))).isEmpty();
    }

    @Test
    void missingProductDimensionsAreFlagged() {
        UUID placementId = UUID.randomUUID();
        assertThat(FurnitureGeometryValidator.validateHasDimensions(placementId, null, 500.0))
                .extracting(i -> i.code())
                .containsExactly(GeometryIssueCode.FURNITURE_MISSING_DIMENSIONS);
    }
}

