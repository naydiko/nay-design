package com.naydiko.backend.dto.response;

import com.naydiko.backend.domain.enums.VendorStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * API representation of a {@link com.naydiko.backend.domain.entity.Vendor}.
 */
public record VendorResponse(
        UUID id,
        String name,
        String country,
        String website,
        String logoUrl,
        VendorStatus status,
        Instant createdAt,
        Instant updatedAt
) {
}

