package com.naydiko.backend.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** Payload for requesting a password reset email. */
public record ForgotPasswordRequest(
        @Email @NotBlank String email
) {
}

