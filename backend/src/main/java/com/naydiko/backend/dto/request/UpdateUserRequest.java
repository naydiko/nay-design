package com.naydiko.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Payload for updating an existing user's profile.
 * Protected/system fields (id, role, status, createdAt, updatedAt) are not
 * client-editable here and are managed separately.
 */
public record UpdateUserRequest(
        @NotBlank @Size(max = 150) String displayName,
        @Size(max = 100) String firstName,
        @Size(max = 100) String lastName,
        @Size(max = 40) String phoneNumber
) {
}

