package com.naydiko.backend.dto.response;

/**
 * Response returned after successful registration or login, carrying the
 * issued JWT and the authenticated user's public profile.
 */
public record AuthResponse(
        String token,
        String tokenType,
        UserResponse user
) {
    public static AuthResponse bearer(String token, UserResponse user) {
        return new AuthResponse(token, "Bearer", user);
    }
}

