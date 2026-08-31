package com.naydiko.backend.dto.response;

import com.naydiko.backend.domain.enums.OpeningDirection;
import com.naydiko.backend.domain.enums.OpeningSwing;
import com.naydiko.backend.domain.enums.OpeningType;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * API representation of an {@link com.naydiko.backend.domain.entity.Opening}.
 */
public record OpeningResponse(
        UUID id,
        UUID wallId,
        OpeningType type,
        BigDecimal offsetFromStartMm,
        BigDecimal widthMm,
        BigDecimal heightMm,
        BigDecimal sillHeightMm,
        OpeningDirection direction,
        OpeningSwing swing
) {
}

