package com.naydiko.backend.geometry.model;

import java.util.UUID;

/**
 * Plain geometric representation of a wall segment, resolved to absolute
 * millimetre coordinates by the caller (the Geometry Engine has no
 * knowledge of {@code Node}/{@code Wall} JPA entities).
 */
public record WallGeometry(
        UUID id,
        UUID startNodeId,
        UUID endNodeId,
        double startX,
        double startY,
        double endX,
        double endY,
        double thicknessMm,
        double heightMm
) {

    public double lengthMm() {
        return Math.hypot(endX - startX, endY - startY);
    }

    /** Angle, in degrees, of the start→end direction measured from the positive X axis. */
    public double angleDegrees() {
        double deg = Math.toDegrees(Math.atan2(endY - startY, endX - startX));
        return deg < 0 ? deg + 360 : deg;
    }

    public BoundingBox2D boundingBox() {
        return asRectangle().boundingBox();
    }

    /** This wall's footprint (length x thickness) as an oriented rectangle. */
    public OrientedRectangle asRectangle() {
        double cx = (startX + endX) / 2.0;
        double cy = (startY + endY) / 2.0;
        return new OrientedRectangle(cx, cy, lengthMm() / 2.0, thicknessMm / 2.0, angleDegrees());
    }
}

