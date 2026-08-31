package com.naydiko.backend.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * API representation of a {@link com.naydiko.backend.domain.entity.Node}.
 */
public record NodeResponse(
        UUID id,
        BigDecimal xMm,
        BigDecimal yMm,
        BigDecimal zMm
) {
}

