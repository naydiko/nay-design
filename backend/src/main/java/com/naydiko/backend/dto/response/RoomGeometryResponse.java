package com.naydiko.backend.dto.response;

import com.naydiko.backend.domain.enums.RoomType;

import java.util.UUID;

/**
 * Lightweight, geometry-focused representation of a
 * {@link com.naydiko.backend.domain.entity.Room}, used within a level's
 * geometry document. Full room details (finishes, ceiling, etc.) are
 * available via the dedicated Room API.
 */
public record RoomGeometryResponse(
        UUID id,
        String name,
        RoomType type
) {
}

