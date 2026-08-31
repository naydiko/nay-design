package com.naydiko.backend.geometry.model;

import java.util.UUID;

/**
 * Plain geometric representation of a furniture item placed in a room:
 * position, real-world footprint (from the referenced product's
 * width/depth, in mm), rotation, and uniform scale. {@code locked} is
 * carried through for context but does not currently affect validation.
 */
public record FurnitureGeometry(
        UUID id,
        UUID productId,
        double centerXMm,
        double centerYMm,
        double widthMm,
        double depthMm,
        double rotationDegrees,
        double scale,
        boolean locked
) {

    /** This item's real-world footprint as an oriented rectangle. */
    public OrientedRectangle footprint() {
        return new OrientedRectangle(
                centerXMm,
                centerYMm,
                (widthMm * scale) / 2.0,
                (depthMm * scale) / 2.0,
                rotationDegrees
        );
    }
}

