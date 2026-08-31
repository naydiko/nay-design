package com.naydiko.backend.dto.response;

import com.naydiko.backend.domain.enums.ProductStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * API representation of a {@link com.naydiko.backend.domain.entity.Product}.
 */
public record ProductResponse(
        UUID id,
        UUID vendorId,
        String externalId,
        String name,
        String sku,
        String category,
        String collection,
        String style,
        String material,
        String color,
        BigDecimal widthMm,
        BigDecimal depthMm,
        BigDecimal heightMm,
        BigDecimal weightGrams,
        BigDecimal priceAmount,
        String priceCurrency,
        ProductStatus status,
        String primaryImageUrl,
        Instant createdAt,
        Instant updatedAt
) {
}

