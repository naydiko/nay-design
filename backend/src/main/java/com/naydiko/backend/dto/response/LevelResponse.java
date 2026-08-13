package com.naydiko.backend.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * API representation of a {@link com.naydiko.backend.domain.entity.Level}.
 */
public record LevelResponse(
        UUID id,
        UUID projectId,
        String name,
        BigDecimal elevationMm,
        Integer orderIndex,
        boolean visible,
        BigDecimal minXMm,
        BigDecimal minYMm,
        BigDecimal maxXMm,
        BigDecimal maxYMm,
        Instant createdAt,
        Instant updatedAt
) {
}

