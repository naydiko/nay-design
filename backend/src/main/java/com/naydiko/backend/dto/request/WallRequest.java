package com.naydiko.backend.dto.request;

import com.naydiko.backend.domain.enums.WallKind;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * A single wall segment submitted as part of a level's geometry. When
 * {@code id} is {@code null}, a new wall is created; otherwise the existing
 * wall with that id is updated. {@code startNodeId}/{@code endNodeId} must
 * reference nodes present in the same geometry submission.
 */
public record WallRequest(
        UUID id,
        @NotNull UUID startNodeId,
        @NotNull UUID endNodeId,
        @NotNull @Positive BigDecimal thicknessMm,
        @NotNull @Positive BigDecimal heightMm,
        @NotNull WallKind kind
) {
}

