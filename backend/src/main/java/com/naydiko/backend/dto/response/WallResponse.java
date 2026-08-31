package com.naydiko.backend.dto.response;

import com.naydiko.backend.domain.enums.WallKind;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * API representation of a {@link com.naydiko.backend.domain.entity.Wall}.
 */
public record WallResponse(
        UUID id,
        UUID startNodeId,
        UUID endNodeId,
        BigDecimal thicknessMm,
        BigDecimal heightMm,
        WallKind kind
) {
}

