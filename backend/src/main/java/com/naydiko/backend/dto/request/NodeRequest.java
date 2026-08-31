package com.naydiko.backend.dto.request;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * A single node (floorplan graph point) submitted as part of a level's
 * geometry. When {@code id} is {@code null}, a new node is created;
 * otherwise the existing node with that id is updated.
 */
public record NodeRequest(
        UUID id,
        @NotNull BigDecimal xMm,
        @NotNull BigDecimal yMm,
        @NotNull BigDecimal zMm
) {
}

