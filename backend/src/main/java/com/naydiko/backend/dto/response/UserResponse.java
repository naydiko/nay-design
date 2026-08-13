package com.naydiko.backend.dto.response;

import com.naydiko.backend.domain.enums.UserRole;
import com.naydiko.backend.domain.enums.UserStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * API representation of a {@link com.naydiko.backend.domain.entity.User}.
 */
public record UserResponse(
        UUID id,
        String email,
        String displayName,
        String firstName,
        String lastName,
        String phoneNumber,
        UserRole role,
        UserStatus status,
        Instant createdAt,
        Instant updatedAt
) {
}

