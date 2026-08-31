package com.naydiko.backend.geometry.model;

/**
 * A rectangle centered at {@code (centerX, centerY)}, rotated by
 * {@code rotationDegrees} around its center, with half-extents along its own
 * local axes ({@code halfExtentX} along the rotated "forward" direction,
 * {@code halfExtentY} perpendicular to it).
 *
 * <p>Used to represent wall footprints (length x thickness), furniture
 * footprints (width x depth), and door clearance zones uniformly, so a
 * single intersection test (via the Separating Axis Theorem) covers walls,
 * furniture, and clearance zones alike.
 */
public record OrientedRectangle(
        double centerX,
        double centerY,
        double halfExtentX,
        double halfExtentY,
        double rotationDegrees
) {

    /** Unit vector along the rectangle's local X axis (its rotated "forward" direction). */
    private double axisXx() {
        return Math.cos(Math.toRadians(rotationDegrees));
    }

    private double axisXy() {
        return Math.sin(Math.toRadians(rotationDegrees));
    }

    /** Unit vector along the rectangle's local Y axis (perpendicular to X). */
    private double axisYx() {
        return -axisXy();
    }

    private double axisYy() {
        return axisXx();
    }

    /** The four corners of the rectangle, in order, as {x, y} pairs. */
    public double[][] corners() {
        double axx = axisXx();
        double axy = axisXy();
        double ayx = axisYx();
        double ayy = axisYy();
        double[][] corners = new double[4][2];
        int i = 0;
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sy = -1; sy <= 1; sy += 2) {
                double ox = sx * halfExtentX;
                double oy = sy * halfExtentY;
                corners[i][0] = centerX + ox * axx + oy * ayx;
                corners[i][1] = centerY + ox * axy + oy * ayy;
                i++;
            }
        }
        return corners;
    }

    public BoundingBox2D boundingBox() {
        double[][] c = corners();
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        for (double[] p : c) {
            minX = Math.min(minX, p[0]);
            minY = Math.min(minY, p[1]);
            maxX = Math.max(maxX, p[0]);
            maxY = Math.max(maxY, p[1]);
        }
        return new BoundingBox2D(minX, minY, maxX, maxY);
    }

    /**
     * Tests whether this rectangle intersects {@code other}, using the
     * Separating Axis Theorem (sufficient and exact for convex polygons such
     * as rectangles): the two shapes overlap iff their projections overlap
     * on every candidate axis (each rectangle's own two edge normals).
     */
    public boolean intersects(OrientedRectangle other) {
        double[][] axes = {
                {axisXx(), axisXy()},
                {axisYx(), axisYy()},
                {other.axisXx(), other.axisXy()},
                {other.axisYx(), other.axisYy()}
        };
        double[][] a = corners();
        double[][] b = other.corners();
        for (double[] axis : axes) {
            double[] rangeA = projectOntoAxis(a, axis);
            double[] rangeB = projectOntoAxis(b, axis);
            if (rangeA[1] < rangeB[0] || rangeB[1] < rangeA[0]) {
                return false;
            }
        }
        return true;
    }

    private static double[] projectOntoAxis(double[][] corners, double[] axis) {
        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        for (double[] c : corners) {
            double d = c[0] * axis[0] + c[1] * axis[1];
            min = Math.min(min, d);
            max = Math.max(max, d);
        }
        return new double[]{min, max};
    }
}

