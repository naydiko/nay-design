package com.naydiko.backend.dto.response;

import java.util.List;
import java.util.UUID;

/**
 * The complete geometry document of a {@link com.naydiko.backend.domain.entity.Level},
 * as consumed by the frontend canvas.
 *
 * <p>{@code warnings} carries any non-blocking Geometry Engine findings from
 * the most recent save (e.g. a room whose walls do not yet form a closed
 * boundary). It is {@code null} on plain reads (GET) and an empty list when
 * a save found nothing to warn about.
 */
public record LevelGeometryResponse(
        UUID levelId,
        List<NodeResponse> nodes,
        List<WallResponse> walls,
        List<OpeningResponse> openings,
        List<RoomGeometryResponse> rooms,
        List<RoomWallResponse> roomWalls,
        List<String> warnings
) {
}

