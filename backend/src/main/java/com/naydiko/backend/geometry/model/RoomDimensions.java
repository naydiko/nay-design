package com.naydiko.backend.geometry.model;

/**
 * Basic footprint dimensions of a room, derived from its bounding box.
 * Stage 1 does not attempt to derive the true (possibly non-rectangular)
 * polygon area — only the enclosing rectangle's width/depth.
 */
public record RoomDimensions(double widthMm, double depthMm) {
}

