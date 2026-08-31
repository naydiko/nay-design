package com.naydiko.backend.dto.request;

import jakarta.validation.constraints.NotBlank;

/** Payload for signing in/up via Google. Carries the Google ID token (JWT) issued by Google, never a raw email/id. */
public record GoogleLoginRequest(
        @NotBlank String idToken
) {
}

