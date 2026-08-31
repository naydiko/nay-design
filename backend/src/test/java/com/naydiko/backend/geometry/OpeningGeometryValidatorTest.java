package com.naydiko.backend.geometry;

import com.naydiko.backend.geometry.model.GeometryIssueCode;
import com.naydiko.backend.geometry.model.OpeningGeometry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OpeningGeometryValidatorTest {

    @Test
    void openingThatFitsOnWallHasNoIssues() {
        OpeningGeometry opening = new OpeningGeometry(UUID.randomUUID(), UUID.randomUUID(), "DOOR", 500, 900, 2100);

        assertThat(OpeningGeometryValidator.validate(opening, 5000.0)).isEmpty();
    }

    @Test
    void openingExceedingWallLengthIsFlagged() {
        UUID openingId = UUID.randomUUID();
        // offset (4500) + width (900) = 5400 > wall length (5000)
        OpeningGeometry opening = new OpeningGeometry(openingId, UUID.randomUUID(), "DOOR", 4500, 900, 2100);

        List<com.naydiko.backend.geometry.model.GeometryValidationIssue> issues =
                OpeningGeometryValidator.validate(opening, 5000.0);

        assertThat(issues)
                .extracting(com.naydiko.backend.geometry.model.GeometryValidationIssue::code)
                .contains(GeometryIssueCode.OPENING_OUT_OF_BOUNDS);
        assertThat(issues)
                .filteredOn(i -> i.code() == GeometryIssueCode.OPENING_OUT_OF_BOUNDS)
                .allSatisfy(i -> assertThat(i.relatedEntityId()).isEqualTo(openingId));
    }

    @Test
    void openingExactlyFillingWallIsValid() {
        OpeningGeometry opening = new OpeningGeometry(UUID.randomUUID(), UUID.randomUUID(), "DOOR", 0, 5000, 2100);

        assertThat(OpeningGeometryValidator.validate(opening, 5000.0)).isEmpty();
    }

    @Test
    void missingWallIsFlagged() {
        OpeningGeometry opening = new OpeningGeometry(UUID.randomUUID(), UUID.randomUUID(), "WINDOW", 0, 900, 1200);

        assertThat(OpeningGeometryValidator.validate(opening, null))
                .extracting(com.naydiko.backend.geometry.model.GeometryValidationIssue::code)
                .containsExactly(GeometryIssueCode.OPENING_MISSING_WALL);
    }

    @Test
    void nonPositiveWidthAndHeightAreFlagged() {
        OpeningGeometry opening = new OpeningGeometry(UUID.randomUUID(), UUID.randomUUID(), "DOOR", 0, -1, 0);

        assertThat(OpeningGeometryValidator.validate(opening, 5000.0))
                .extracting(com.naydiko.backend.geometry.model.GeometryValidationIssue::code)
                .contains(GeometryIssueCode.OPENING_NON_POSITIVE_WIDTH, GeometryIssueCode.OPENING_NON_POSITIVE_HEIGHT);
    }
}

