package com.naydiko.backend.dto.response;

/** Generic success/status message, used for endpoints that must not leak account existence or state. */
public record MessageResponse(String message) {
}

