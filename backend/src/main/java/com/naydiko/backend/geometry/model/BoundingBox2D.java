package com.naydiko.backend.geometry.model;

/**
 * An axis-aligned bounding box in the level's millimetre coordinate system.
 */
public record BoundingBox2D(double minX, double minY, double maxX, double maxY) {

    public double width() {
        return maxX - minX;
    }

    public double height() {
        return maxY - minY;
    }

    public boolean contains(double x, double y, double epsilon) {
        return x >= minX - epsilon && x <= maxX + epsilon && y >= minY - epsilon && y <= maxY + epsilon;
    }

    public BoundingBox2D union(BoundingBox2D other) {
        return new BoundingBox2D(
                Math.min(minX, other.minX),
                Math.min(minY, other.minY),
                Math.max(maxX, other.maxX),
                Math.max(maxY, other.maxY)
        );
    }

    public static BoundingBox2D of(double x1, double y1, double x2, double y2) {
        return new BoundingBox2D(Math.min(x1, x2), Math.min(y1, y2), Math.max(x1, x2), Math.max(y1, y2));
    }
}

