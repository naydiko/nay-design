package com.naydiko.backend.domain.enums;

/**
 * How a {@code app_user} authenticates. {@code LOCAL} accounts have a
 * password hash; {@code GOOGLE} accounts authenticate via Google OIDC and
 * may not have a usable local password.
 */
public enum AuthProvider {
    LOCAL,
    GOOGLE
}

