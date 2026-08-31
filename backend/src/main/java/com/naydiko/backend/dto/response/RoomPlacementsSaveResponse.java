package com.naydiko.backend.dto.response;

import java.util.List;

/**
 * Response body for saving a room's furniture layout: the saved placements
 * plus any non-blocking Geometry Engine findings about the layout (fit
 * within the room, wall/furniture intersections, door clearance). Stage 1
 * never rejects a save for these — they are surfaced so the frontend can
 * show them to the user.
 */
public record RoomPlacementsSaveResponse(
        List<FurniturePlacementResponse> placements,
        List<GeometryIssueResponse> issues
) {
}

