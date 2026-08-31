package com.naydiko.backend.service;

import com.naydiko.backend.domain.entity.EmailVerificationToken;
import com.naydiko.backend.domain.entity.User;
import com.naydiko.backend.domain.repository.EmailVerificationTokenRepository;
import com.naydiko.backend.exception.InvalidTokenException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;

/**
 * Handles email verification for LOCAL (email/password) registrations.
 * Google accounts are considered verified already (Google/OIDC vouches for
 * the email) and never go through this flow.
 */
@Service
@Transactional(readOnly = true)
public class EmailVerificationService {

    private final EmailVerificationTokenRepository tokenRepository;
    private final AuthNotificationService notificationService;
    private final long expirationMs;
    private final SecureRandom secureRandom = new SecureRandom();

    public EmailVerificationService(
            EmailVerificationTokenRepository tokenRepository,
            AuthNotificationService notificationService,
            @Value("${app.auth.verification-token-expiration-ms:86400000}") long expirationMs) {
        this.tokenRepository = tokenRepository;
        this.notificationService = notificationService;
        this.expirationMs = expirationMs;
    }

    @Transactional
    public void sendVerificationEmail(User user, String frontendBaseUrl) {
        String rawToken = generateRawToken();
        EmailVerificationToken token = EmailVerificationToken.builder()
                .user(user)
                .tokenHash(hash(rawToken))
                .expiresAt(Instant.now().plusMillis(expirationMs))
                .build();
        tokenRepository.save(token);

        String link = frontendBaseUrl + "/verify-email?token=" + rawToken;
        notificationService.sendVerificationEmail(user.getEmail(), user.getDisplayName(), link);
    }

    @Transactional
    public void verifyEmail(String rawToken) {
        EmailVerificationToken token = tokenRepository.findByTokenHash(hash(rawToken))
                .orElseThrow(() -> new InvalidTokenException("Invalid or expired verification token"));

        if (token.isUsed()) {
            throw new InvalidTokenException("This verification token has already been used");
        }
        if (token.isExpired()) {
            throw new InvalidTokenException("This verification token has expired");
        }

        User user = token.getUser();
        if (user.getEmailVerifiedAt() == null) {
            user.setEmailVerifiedAt(Instant.now());
        }
        token.setUsedAt(Instant.now());
    }

    private String generateRawToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawToken.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hashed);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }
}

