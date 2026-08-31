package com.naydiko.backend.dto.request;

import com.naydiko.backend.domain.enums.OpeningDirection;
import com.naydiko.backend.domain.enums.OpeningSwing;
import com.naydiko.backend.domain.enums.OpeningType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * A single door/window/archway opening submitted as part of a level's
 * geometry. When {@code id} is {@code null}, a new opening is created;
 * otherwise the existing opening with that id is updated. {@code wallId}
 * must reference a wall present in the same geometry submission.
 */
public record OpeningRequest(
        UUID id,
        @NotNull UUID wallId,
        @NotNull OpeningType type,
        @NotNull @PositiveOrZero BigDecimal offsetFromStartMm,
        @NotNull @Positive BigDecimal widthMm,
        @NotNull @Positive BigDecimal heightMm,
        @PositiveOrZero BigDecimal sillHeightMm,
        OpeningDirection direction,
        OpeningSwing swing
) {
}

