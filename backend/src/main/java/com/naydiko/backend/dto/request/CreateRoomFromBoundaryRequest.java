package com.naydiko.backend.dto.request;

import com.naydiko.backend.domain.enums.RoomType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/** Creates a room and its boundary links to existing walls in one transaction. */
public record CreateRoomFromBoundaryRequest(
        @NotBlank @Size(max = 160) String name,
        @NotNull RoomType type,
        @NotEmpty @Size(min = 3) List<@NotNull UUID> wallIds
) {
}
