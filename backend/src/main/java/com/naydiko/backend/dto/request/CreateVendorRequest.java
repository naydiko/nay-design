package com.naydiko.backend.dto.request;

import com.naydiko.backend.domain.enums.VendorStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Payload for creating a new vendor.
 */
public record CreateVendorRequest(
        @NotBlank @Size(max = 160) String name,
        @Size(max = 100) String country,
        @Size(max = 2048) String website,
        @Size(max = 2048) String logoUrl,
        @NotNull VendorStatus status
) {
}

