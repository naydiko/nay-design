package com.naydiko.backend.dto.response;

import java.util.List;
import java.util.UUID;

/**
 * The complete geometry document of a {@link com.naydiko.backend.domain.entity.Level},
 * as consumed by the frontend canvas.
 */
public record LevelGeometryResponse(
        UUID levelId,
        List<NodeResponse> nodes,
        List<WallResponse> walls,
        List<OpeningResponse> openings,
        List<RoomGeometryResponse> rooms,
        List<RoomWallResponse> roomWalls
) {
}

