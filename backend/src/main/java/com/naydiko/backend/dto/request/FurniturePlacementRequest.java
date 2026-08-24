package com.naydiko.backend.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * A single furniture item placement, used when saving a room's furniture layout.
 */
public record FurniturePlacementRequest(
        @NotNull UUID productId,
        @NotNull BigDecimal xMm,
        @NotNull BigDecimal yMm,
        @NotNull BigDecimal zMm,
        @NotNull @DecimalMin("0.00") BigDecimal rotationAngle,
        @NotNull @DecimalMin("0.0001") BigDecimal scale,
        boolean locked
) {
}

