package com.naydiko.backend.dto.request;

import com.naydiko.backend.domain.enums.ProductStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Payload for creating a new product.
 */
public record CreateProductRequest(
        @NotNull UUID vendorId,
        @Size(max = 120) String externalId,
        @NotBlank @Size(max = 180) String name,
        @Size(max = 100) String sku,
        @NotBlank @Size(max = 120) String category,
        @Size(max = 160) String collection,
        @Size(max = 120) String style,
        @Size(max = 120) String material,
        @Size(max = 120) String color,
        @Positive BigDecimal widthMm,
        @Positive BigDecimal depthMm,
        @Positive BigDecimal heightMm,
        @PositiveOrZero BigDecimal weightGrams,
        @DecimalMin("0.00") BigDecimal priceAmount,
        @Size(min = 3, max = 3) String priceCurrency,
        @NotNull ProductStatus status
) {
}

