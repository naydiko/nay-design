package com.naydiko.backend.domain.enums;

/**
 * Structural classification of a {@code wall}, used to drive rendering,
 * validation, and structural-change safeguards.
 */
public enum WallKind {
    INTERIOR,
    EXTERIOR,
    LOAD_BEARING,
    PARTITION
}

