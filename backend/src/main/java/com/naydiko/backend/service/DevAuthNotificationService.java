package com.naydiko.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Development-only {@link AuthNotificationService}: logs the link instead
 * of sending a real email, so the reset/verification flows can be tested
 * end-to-end locally without a production email provider configured.
 *
 * <p>Never logs passwords or raw tokens beyond what's embedded in the link
 * itself (which is expected to be shared with the account owner anyway).
 */
@Service
public class DevAuthNotificationService implements AuthNotificationService {

    private static final Logger log = LoggerFactory.getLogger(DevAuthNotificationService.class);

    @Override
    public void sendPasswordResetEmail(String toEmail, String displayName, String resetLink) {
        log.info("[DEV EMAIL] Password reset requested for {} ({}). Reset link: {}", displayName, toEmail, resetLink);
    }

    @Override
    public void sendVerificationEmail(String toEmail, String displayName, String verificationLink) {
        log.info("[DEV EMAIL] Verify email for {} ({}). Verification link: {}", displayName, toEmail, verificationLink);
    }
}

