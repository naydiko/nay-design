package com.naydiko.backend.geometry.model;

/**
 * Stable machine-readable identifiers for {@link GeometryValidationIssue}s
 * raised by the {@link com.naydiko.backend.geometry.GeometryEngine} and its
 * calculators/validators. Intended to eventually be surfaced to the frontend
 * so it can react to specific problems rather than parsing free-text messages.
 */
public enum GeometryIssueCode {

    // Wall
    WALL_INVALID_NODES,
    WALL_ZERO_LENGTH,
    WALL_NON_POSITIVE_THICKNESS,
    WALL_NON_POSITIVE_HEIGHT,

    // Opening
    OPENING_MISSING_WALL,
    OPENING_NON_POSITIVE_WIDTH,
    OPENING_NON_POSITIVE_HEIGHT,
    OPENING_INVALID_OFFSET,
    OPENING_OUT_OF_BOUNDS,

    // Room
    ROOM_NOT_CLOSED,

    // Furniture
    FURNITURE_MISSING_PRODUCT,
    FURNITURE_MISSING_DIMENSIONS,
    FURNITURE_OUTSIDE_ROOM,
    FURNITURE_INTERSECTS_WALL,
    FURNITURE_INTERSECTS_FURNITURE,

    // Door clearance
    DOOR_BLOCKED
}

