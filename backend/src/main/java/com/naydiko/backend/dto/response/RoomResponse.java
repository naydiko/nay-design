package com.naydiko.backend.dto.response;

import com.naydiko.backend.domain.enums.CeilingType;
import com.naydiko.backend.domain.enums.RoomType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * API representation of a {@link com.naydiko.backend.domain.entity.Room}.
 */
public record RoomResponse(
        UUID id,
        UUID levelId,
        String name,
        RoomType type,
        String floorFinish,
        String wallFinish,
        String ceilingFinish,
        CeilingType ceilingType,
        BigDecimal ceilingHeightMm,
        Instant createdAt,
        Instant updatedAt
) {
}

