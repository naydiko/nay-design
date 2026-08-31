package com.naydiko.backend.geometry;

import com.naydiko.backend.geometry.model.RoomBoundary;
import com.naydiko.backend.geometry.model.RoomGeometryAnalysis;
import com.naydiko.backend.geometry.model.WallGeometry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RoomGeometryCalculatorTest {

    @Test
    void fourWallsFormingARectangleAreClosed() {
        UUID n1 = UUID.randomUUID();
        UUID n2 = UUID.randomUUID();
        UUID n3 = UUID.randomUUID();
        UUID n4 = UUID.randomUUID();
        List<WallGeometry> walls = List.of(
                new WallGeometry(UUID.randomUUID(), n1, n2, 0, 0, 4000, 0, 100, 2700),
                new WallGeometry(UUID.randomUUID(), n2, n3, 4000, 0, 4000, 3000, 100, 2700),
                new WallGeometry(UUID.randomUUID(), n3, n4, 4000, 3000, 0, 3000, 100, 2700),
                new WallGeometry(UUID.randomUUID(), n4, n1, 0, 3000, 0, 0, 100, 2700)
        );
        RoomBoundary boundary = new RoomBoundary(UUID.randomUUID(), walls);

        assertThat(RoomGeometryCalculator.isClosed(boundary)).isTrue();

        RoomGeometryAnalysis analysis = RoomGeometryCalculator.analyze(boundary);
        assertThat(analysis.closed()).isTrue();
        assertThat(analysis.issues()).isEmpty();
        assertThat(analysis.dimensions().widthMm()).isCloseTo(4100.0, org.assertj.core.data.Offset.offset(0.01)); // 4000 + 2*50 (thickness/2)
        assertThat(analysis.dimensions().depthMm()).isCloseTo(3100.0, org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    void openBoundaryIsNotClosedAndRaisesWarning() {
        UUID n1 = UUID.randomUUID();
        UUID n2 = UUID.randomUUID();
        UUID n3 = UUID.randomUUID();
        // Only three walls of a rectangle: one side is missing.
        List<WallGeometry> walls = List.of(
                new WallGeometry(UUID.randomUUID(), n1, n2, 0, 0, 4000, 0, 100, 2700),
                new WallGeometry(UUID.randomUUID(), n2, n3, 4000, 0, 4000, 3000, 100, 2700)
        );
        RoomBoundary boundary = new RoomBoundary(UUID.randomUUID(), walls);

        assertThat(RoomGeometryCalculator.isClosed(boundary)).isFalse();
        assertThat(RoomGeometryCalculator.analyze(boundary).issues())
                .extracting(i -> i.code())
                .contains(com.naydiko.backend.geometry.model.GeometryIssueCode.ROOM_NOT_CLOSED);
    }

    @Test
    void emptyBoundaryIsNotClosedWithoutIssues() {
        RoomBoundary boundary = new RoomBoundary(UUID.randomUUID(), List.of());

        assertThat(RoomGeometryCalculator.isClosed(boundary)).isFalse();
        assertThat(RoomGeometryCalculator.boundingBox(boundary)).isNull();
        // No walls at all -> nothing meaningful to warn about yet.
        assertThat(RoomGeometryCalculator.analyze(boundary).issues()).isEmpty();
    }
}


