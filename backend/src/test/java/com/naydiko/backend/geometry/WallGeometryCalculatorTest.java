package com.naydiko.backend.geometry;

import com.naydiko.backend.geometry.model.GeometryIssueCode;
import com.naydiko.backend.geometry.model.GeometrySeverity;
import com.naydiko.backend.geometry.model.GeometryValidationIssue;
import com.naydiko.backend.geometry.model.WallGeometry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class WallGeometryCalculatorTest {

    @Test
    void validWallHasNoIssuesAndCorrectCalculations() {
        WallGeometry wall = new WallGeometry(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                0, 0, 3000, 4000, 150, 2700);

        assertThat(WallGeometryCalculator.validate(wall)).isEmpty();
        assertThat(wall.lengthMm()).isEqualTo(5000.0); // 3-4-5 triangle
        assertThat(wall.angleDegrees()).isCloseTo(53.13, org.assertj.core.data.Offset.offset(0.1));
        assertThat(wall.boundingBox().width()).isGreaterThan(0);
        assertThat(wall.boundingBox().height()).isGreaterThan(0);
    }

    @Test
    void zeroLengthWallIsFlagged() {
        UUID wallId = UUID.randomUUID();
        WallGeometry wall = new WallGeometry(
                wallId, UUID.randomUUID(), UUID.randomUUID(),
                1000, 1000, 1000, 1000, 150, 2700);

        List<GeometryValidationIssue> issues = WallGeometryCalculator.validate(wall);

        assertThat(issues)
                .extracting(GeometryValidationIssue::code)
                .contains(GeometryIssueCode.WALL_ZERO_LENGTH);
        assertThat(issues)
                .filteredOn(i -> i.code() == GeometryIssueCode.WALL_ZERO_LENGTH)
                .allSatisfy(i -> {
                    assertThat(i.severity()).isEqualTo(GeometrySeverity.ERROR);
                    assertThat(i.relatedEntityId()).isEqualTo(wallId);
                });
    }

    @Test
    void sameStartAndEndNodeIsFlagged() {
        UUID sharedNode = UUID.randomUUID();
        WallGeometry wall = new WallGeometry(
                UUID.randomUUID(), sharedNode, sharedNode,
                0, 0, 1000, 1000, 150, 2700);

        assertThat(WallGeometryCalculator.validate(wall))
                .extracting(GeometryValidationIssue::code)
                .contains(GeometryIssueCode.WALL_INVALID_NODES);
    }

    @Test
    void nonPositiveThicknessAndHeightAreFlagged() {
        WallGeometry wall = new WallGeometry(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                0, 0, 1000, 0, 0, -5);

        List<GeometryValidationIssue> issues = WallGeometryCalculator.validate(wall);

        assertThat(issues)
                .extracting(GeometryValidationIssue::code)
                .contains(GeometryIssueCode.WALL_NON_POSITIVE_THICKNESS, GeometryIssueCode.WALL_NON_POSITIVE_HEIGHT);
    }
}

