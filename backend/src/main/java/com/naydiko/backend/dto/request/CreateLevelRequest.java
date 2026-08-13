package com.naydiko.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Payload for creating a new level within a project.
 */
public record CreateLevelRequest(
        @NotNull UUID projectId,
        @NotBlank @Size(max = 120) String name,
        BigDecimal elevationMm,
        Integer orderIndex
) {
}

