package com.naydiko.backend.dto.request;

import com.naydiko.backend.domain.enums.ProjectType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Payload for creating a new project.
 */
public record CreateProjectRequest(
        @NotNull UUID ownerId,
        @NotBlank @Size(max = 160) String name,
        String description,
        @NotNull ProjectType projectType,
        @DecimalMin("0.00") BigDecimal budgetMin,
        @DecimalMin("0.00") BigDecimal budgetMax,
        @Size(min = 3, max = 3) String currency
) {
}

