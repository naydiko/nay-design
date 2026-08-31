package com.naydiko.backend.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Payload for registering a new user account.
 */
public record RegisterRequest(
        @Email @NotBlank @Size(max = 320) String email,
        @NotBlank @Size(max = 150) String displayName,
        @Size(max = 100) String firstName,
        @Size(max = 100) String lastName,
        @Size(max = 40) String phoneNumber,
        @NotBlank @Size(min = 8, max = 100) String password
) {
}

