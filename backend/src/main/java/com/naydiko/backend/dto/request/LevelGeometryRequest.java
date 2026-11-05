package com.naydiko.backend.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * The complete geometry document of a {@link com.naydiko.backend.domain.entity.Level},
 * as edited by the frontend canvas and saved in one shot.
 *
 * <p>Nodes, walls, and openings are reconciled as structural level geometry.
 * Rooms are only reconciled when explicitly present so ordinary geometry
 * saves cannot delete unrelated rooms, room-wall links, or furniture.
 */
public record LevelGeometryRequest(
        @NotNull List<@Valid NodeRequest> nodes,
        @NotNull List<@Valid WallRequest> walls,
        @NotNull List<@Valid OpeningRequest> openings,
        @NotNull List<@Valid RoomGeometryRequest> rooms,
        @NotNull List<@Valid RoomWallRequest> roomWalls
) {
}

