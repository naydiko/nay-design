package com.naydiko.backend.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * A single room-wall border relationship submitted as part of a level's
 * geometry. Both ids must reference a room/wall present in the same
 * geometry submission.
 */
public record RoomWallRequest(
        @NotNull UUID roomId,
        @NotNull UUID wallId
) {
}

