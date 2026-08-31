package com.naydiko.backend.service;

import com.naydiko.backend.domain.entity.PasswordResetToken;
import com.naydiko.backend.domain.entity.User;
import com.naydiko.backend.domain.repository.PasswordResetTokenRepository;
import com.naydiko.backend.domain.repository.UserRepository;
import com.naydiko.backend.exception.InvalidTokenException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

/**
 * Handles the forgot-password / reset-password flow.
 *
 * <p>Security notes:
 * <ul>
 *   <li>The plaintext token is generated with {@link SecureRandom} and is only
 *       ever returned to the caller (to email) — the database only stores a
 *       SHA-256 hash of it, so a database leak does not expose usable tokens.</li>
 *   <li>Tokens are single-use ({@code usedAt}) and time-limited.</li>
 *   <li>{@link #requestReset} never reveals whether the email exists.</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class PasswordResetService {

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthNotificationService notificationService;
    private final long expirationMs;
    private final SecureRandom secureRandom = new SecureRandom();

    public PasswordResetService(
            UserRepository userRepository,
            PasswordResetTokenRepository tokenRepository,
            PasswordEncoder passwordEncoder,
            AuthNotificationService notificationService,
            @Value("${app.auth.reset-token-expiration-ms:1800000}") long expirationMs) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.notificationService = notificationService;
        this.expirationMs = expirationMs;
    }

    /**
     * Generates and emails a reset token if (and only if) the email belongs
     * to a local account. Always returns normally — never throws for an
     * unknown email — so callers must return the same generic response
     * regardless of the outcome.
     */
    @Transactional
    public void requestReset(String email, String frontendBaseUrl) {
        Optional<User> maybeUser = userRepository.findByEmail(email);
        if (maybeUser.isEmpty()) {
            return;
        }
        User user = maybeUser.get();

        String rawToken = generateRawToken();
        PasswordResetToken token = PasswordResetToken.builder()
                .user(user)
                .tokenHash(hash(rawToken))
                .expiresAt(Instant.now().plusMillis(expirationMs))
                .build();
        tokenRepository.save(token);

        String link = frontendBaseUrl + "/reset-password?token=" + rawToken;
        notificationService.sendPasswordResetEmail(user.getEmail(), user.getDisplayName(), link);
    }

    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        PasswordResetToken token = tokenRepository.findByTokenHash(hash(rawToken))
                .orElseThrow(() -> new InvalidTokenException("Invalid or expired reset token"));

        if (token.isUsed()) {
            throw new InvalidTokenException("This reset token has already been used");
        }
        if (token.isExpired()) {
            throw new InvalidTokenException("This reset token has expired");
        }

        User user = token.getUser();
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        token.setUsedAt(Instant.now());
    }

    private String generateRawToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawToken.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hashed);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }
}

