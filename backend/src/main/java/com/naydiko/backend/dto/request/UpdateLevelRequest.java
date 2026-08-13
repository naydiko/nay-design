package com.naydiko.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Payload for updating an existing level.
 */
public record UpdateLevelRequest(
        @NotBlank @Size(max = 120) String name,
        BigDecimal elevationMm,
        @NotNull Integer orderIndex,
        boolean visible
) {
}

