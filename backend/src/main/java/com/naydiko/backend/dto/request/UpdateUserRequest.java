package com.naydiko.backend.dto.request;

import com.naydiko.backend.domain.enums.UserRole;
import com.naydiko.backend.domain.enums.UserStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Payload for updating an existing user's profile/status.
 */
public record UpdateUserRequest(
        @NotBlank @Size(max = 150) String displayName,
        @Size(max = 100) String firstName,
        @Size(max = 100) String lastName,
        @Size(max = 40) String phoneNumber,
        UserRole role,
        UserStatus status
) {
}

