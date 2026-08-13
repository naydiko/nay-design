package com.naydiko.backend.exception;

/**
 * Thrown when an operation would violate a uniqueness constraint
 * (e.g. duplicate email, vendor name, or product external id).
 */
public class DuplicateResourceException extends RuntimeException {

    public DuplicateResourceException(String message) {
        super(message);
    }
}

