package com.naydiko.backend.dto.response;

import com.naydiko.backend.domain.enums.ProjectStatus;
import com.naydiko.backend.domain.enums.ProjectType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * API representation of a {@link com.naydiko.backend.domain.entity.Project}.
 */
public record ProjectResponse(
        UUID id,
        UUID ownerId,
        String name,
        String description,
        ProjectType projectType,
        ProjectStatus status,
        BigDecimal budgetMin,
        BigDecimal budgetMax,
        String currency,
        Instant createdAt,
        Instant updatedAt
) {
}

