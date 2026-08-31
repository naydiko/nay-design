package com.naydiko.backend.security;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.naydiko.backend.exception.InvalidCredentialsException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.GeneralSecurityException;
import java.util.Collections;

/**
 * Verifies Google Sign-In ID tokens using Google's official client library
 * and public keys (standard OIDC token verification) — the backend never
 * trusts an email/user id supplied directly by the frontend.
 */
@Service
public class GoogleIdTokenVerifierService {

    private final GoogleIdTokenVerifier verifier;
    private final boolean configured;

    public GoogleIdTokenVerifierService(@Value("${app.google.client-id:}") String googleClientId) {
        this.configured = googleClientId != null && !googleClientId.isBlank();
        this.verifier = configured
                ? new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance())
                        .setAudience(Collections.singletonList(googleClientId))
                        .build()
                : null;
    }

    /** Verifies the given Google ID token and returns its validated payload. */
    public GoogleIdToken.Payload verify(String idToken) {
        if (!configured) {
            throw new IllegalStateException(
                    "Google sign-in is not configured on this server (app.google.client-id is unset)");
        }
        try {
            GoogleIdToken token = verifier.verify(idToken);
            if (token == null) {
                throw new InvalidCredentialsException("Invalid Google credential");
            }
            return token.getPayload();
        } catch (GeneralSecurityException | java.io.IOException | IllegalArgumentException ex) {
            throw new InvalidCredentialsException("Invalid Google credential");
        }
    }
}

