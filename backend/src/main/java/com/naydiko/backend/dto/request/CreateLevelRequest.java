package com.naydiko.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Payload for creating a new level within a project.
 * The owning project is identified via the {@code /api/projects/{projectId}/levels} path.
 */
public record CreateLevelRequest(
        @NotBlank @Size(max = 120) String name,
        BigDecimal elevationMm,
        Integer orderIndex
) {
}

