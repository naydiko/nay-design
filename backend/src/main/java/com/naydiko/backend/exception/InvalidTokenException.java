package com.naydiko.backend.exception;

/**
 * Thrown when a password reset / email verification token is missing,
 * expired, or already used.
 */
public class InvalidTokenException extends RuntimeException {

    public InvalidTokenException(String message) {
        super(message);
    }
}

