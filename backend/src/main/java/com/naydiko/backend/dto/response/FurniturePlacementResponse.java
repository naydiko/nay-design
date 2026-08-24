package com.naydiko.backend.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * API representation of a {@link com.naydiko.backend.domain.entity.FurniturePlacement}.
 */
public record FurniturePlacementResponse(
        UUID id,
        UUID roomId,
        UUID productId,
        BigDecimal xMm,
        BigDecimal yMm,
        BigDecimal zMm,
        BigDecimal rotationAngle,
        BigDecimal scale,
        boolean locked,
        Instant createdAt,
        Instant updatedAt
) {
}

