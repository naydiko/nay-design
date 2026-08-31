package com.naydiko.backend.exception;

/**
 * Thrown when login credentials are invalid or the account cannot authenticate.
 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException(String message) {
        super(message);
    }
}

