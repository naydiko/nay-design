package com.naydiko.backend.dto.request;

import com.naydiko.backend.domain.enums.RoomType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * A room submitted as part of a level's geometry. When {@code id} is
 * {@code null}, a new room is created; otherwise the existing room with
 * that id is updated. Only geometry-relevant fields (name, type) are
 * managed here; finishes and other attributes are left untouched and
 * remain the responsibility of the dedicated Room API.
 */
public record RoomGeometryRequest(
        UUID id,
        @NotBlank @Size(max = 160) String name,
        @NotNull RoomType type
) {
}

