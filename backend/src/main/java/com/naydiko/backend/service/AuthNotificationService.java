package com.naydiko.backend.service;

/**
 * Abstraction over "send this user an email" so the auth flows (password
 * reset, email verification) never depend on a concrete email provider.
 *
 * <p>Stage 1 ships {@code DevAuthNotificationService}, which just logs the
 * link. Wire in a real provider (SES, SendGrid, Postmark, SMTP...) later by
 * providing an alternate {@link AuthNotificationService} bean — no changes
 * needed in {@code AuthService}/{@code PasswordResetService}.
 */
public interface AuthNotificationService {

    void sendPasswordResetEmail(String toEmail, String displayName, String resetLink);

    void sendVerificationEmail(String toEmail, String displayName, String verificationLink);
}

