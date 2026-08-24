package com.naydiko.backend.dto.request;

import com.naydiko.backend.domain.enums.CeilingType;
import com.naydiko.backend.domain.enums.RoomType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Payload for creating a new room within a level.
 * The owning level is identified via the {@code /api/levels/{levelId}/rooms} path.
 */
public record CreateRoomRequest(
        @NotBlank @Size(max = 160) String name,
        @NotNull RoomType type,
        @Size(max = 120) String floorFinish,
        @Size(max = 120) String wallFinish,
        @Size(max = 120) String ceilingFinish,
        CeilingType ceilingType,
        @Positive BigDecimal ceilingHeightMm
) {
}

