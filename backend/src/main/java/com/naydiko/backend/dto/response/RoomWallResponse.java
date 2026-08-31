package com.naydiko.backend.dto.response;

import java.util.UUID;

/**
 * A single room-wall border relationship, as persisted in the {@code room_wall} join table.
 */
public record RoomWallResponse(
        UUID roomId,
        UUID wallId
) {
}

