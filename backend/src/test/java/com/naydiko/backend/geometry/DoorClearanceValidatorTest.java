package com.naydiko.backend.geometry;

import com.naydiko.backend.geometry.model.FurnitureGeometry;
import com.naydiko.backend.geometry.model.GeometryIssueCode;
import com.naydiko.backend.geometry.model.OpeningGeometry;
import com.naydiko.backend.geometry.model.WallGeometry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DoorClearanceValidatorTest {

    @Test
    void furnitureBlockingDoorClearanceIsFlagged() {
        UUID wallId = UUID.randomUUID();
        // South wall from (0,0) to (4000,0): direction along +X, interior of the room is +Y.
        WallGeometry wall = new WallGeometry(wallId, UUID.randomUUID(), UUID.randomUUID(), 0, 0, 4000, 0, 100, 2700);
        OpeningGeometry door = new OpeningGeometry(UUID.randomUUID(), wallId, "DOOR", 1000, 900, 2100);

        // Sits right in front of the door, inside the room.
        FurnitureGeometry blocker = new FurnitureGeometry(
                UUID.randomUUID(), UUID.randomUUID(), 1450, 300, 600, 600, 0, 1, false);

        List<com.naydiko.backend.geometry.model.GeometryValidationIssue> issues =
                DoorClearanceValidator.validate(door, wall, List.of(blocker), 700);

        assertThat(issues)
                .extracting(i -> i.code())
                .containsExactly(GeometryIssueCode.DOOR_BLOCKED);
    }

    @Test
    void furnitureAwayFromDoorDoesNotBlockIt() {
        UUID wallId = UUID.randomUUID();
        WallGeometry wall = new WallGeometry(wallId, UUID.randomUUID(), UUID.randomUUID(), 0, 0, 4000, 0, 100, 2700);
        OpeningGeometry door = new OpeningGeometry(UUID.randomUUID(), wallId, "DOOR", 1000, 900, 2100);

        FurnitureGeometry farItem = new FurnitureGeometry(
                UUID.randomUUID(), UUID.randomUUID(), 3500, 2500, 500, 500, 0, 1, false);

        assertThat(DoorClearanceValidator.validate(door, wall, List.of(farItem), 700)).isEmpty();
    }

    @Test
    void windowsDoNotRequireClearance() {
        UUID wallId = UUID.randomUUID();
        WallGeometry wall = new WallGeometry(wallId, UUID.randomUUID(), UUID.randomUUID(), 0, 0, 4000, 0, 100, 2700);
        OpeningGeometry window = new OpeningGeometry(UUID.randomUUID(), wallId, "WINDOW", 1000, 900, 1200);

        FurnitureGeometry blocker = new FurnitureGeometry(
                UUID.randomUUID(), UUID.randomUUID(), 1450, 300, 600, 600, 0, 1, false);

        assertThat(DoorClearanceValidator.validate(window, wall, List.of(blocker), 700)).isEmpty();
    }
}

