package com.naydiko.backend.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * The complete geometry document of a {@link com.naydiko.backend.domain.entity.Level},
 * as edited by the frontend canvas and saved in one shot.
 *
 * <p>Entities present with an existing id are updated, entities with a
 * {@code null} id are created, and any previously-persisted entity for the
 * level that is absent from the submitted lists is deleted.
 */
public record LevelGeometryRequest(
        @NotNull List<@Valid NodeRequest> nodes,
        @NotNull List<@Valid WallRequest> walls,
        @NotNull List<@Valid OpeningRequest> openings,
        @NotNull List<@Valid RoomGeometryRequest> rooms,
        @NotNull List<@Valid RoomWallRequest> roomWalls
) {
}

