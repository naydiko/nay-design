package com.naydiko.backend.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Payload for authenticating with email and password.
 */
public record LoginRequest(
        @Email @NotBlank String email,
        @NotBlank String password
) {
}

