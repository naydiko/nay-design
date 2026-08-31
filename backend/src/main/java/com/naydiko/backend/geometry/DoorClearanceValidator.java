package com.naydiko.backend.geometry;

import com.naydiko.backend.geometry.model.FurnitureGeometry;
import com.naydiko.backend.geometry.model.GeometryIssueCode;
import com.naydiko.backend.geometry.model.GeometryValidationIssue;
import com.naydiko.backend.geometry.model.OpeningGeometry;
import com.naydiko.backend.geometry.model.OrientedRectangle;
import com.naydiko.backend.geometry.model.WallGeometry;

import java.util.ArrayList;
import java.util.List;

/**
 * Basic clearance/blocked-area concept for doors: computes the rectangular
 * zone a door needs to swing/pass through, and detects whether furniture
 * occupies it.
 *
 * <p>The clearance depth is a caller-supplied, configurable value (e.g. from
 * application configuration) — this class does not hard-code any
 * interior-design rule; it only works from the door's actual geometry (its
 * position and width along the wall) and the given clearance depth.
 */
public final class DoorClearanceValidator {

    private DoorClearanceValidator() {
    }

    /**
     * Computes the clearance zone for a door: a rectangle as wide as the
     * door opening, extending {@code clearanceDepthMm} outward from the
     * wall's outer face (in the direction of the wall's positive normal).
     */
    public static OrientedRectangle computeClearanceZone(
            OpeningGeometry door, WallGeometry wall, double clearanceDepthMm) {
        double angleRad = Math.toRadians(wall.angleDegrees());
        double dirX = Math.cos(angleRad);
        double dirY = Math.sin(angleRad);
        // Perpendicular ("normal") direction, rotated +90 degrees from the wall direction.
        double normX = -dirY;
        double normY = dirX;

        double openingCenterOffset = door.offsetFromStartMm() + door.widthMm() / 2.0;
        double openingCenterX = wall.startX() + dirX * openingCenterOffset;
        double openingCenterY = wall.startY() + dirY * openingCenterOffset;

        double distanceFromWallCenterline = wall.thicknessMm() / 2.0 + clearanceDepthMm / 2.0;
        double zoneCenterX = openingCenterX + normX * distanceFromWallCenterline;
        double zoneCenterY = openingCenterY + normY * distanceFromWallCenterline;

        return new OrientedRectangle(
                zoneCenterX,
                zoneCenterY,
                door.widthMm() / 2.0,
                clearanceDepthMm / 2.0,
                wall.angleDegrees()
        );
    }

    /**
     * Detects furniture items that occupy the door's clearance zone,
     * raising {@link GeometryIssueCode#DOOR_BLOCKED} (warning) for each.
     */
    public static List<GeometryValidationIssue> validate(
            OpeningGeometry door, WallGeometry wall, List<FurnitureGeometry> furniture, double clearanceDepthMm) {
        List<GeometryValidationIssue> issues = new ArrayList<>();
        if (!door.isDoor()) {
            return issues;
        }
        OrientedRectangle clearanceZone = computeClearanceZone(door, wall, clearanceDepthMm);
        for (FurnitureGeometry item : furniture) {
            if (item.footprint().intersects(clearanceZone)) {
                issues.add(GeometryValidationIssue.warning(
                        GeometryIssueCode.DOOR_BLOCKED,
                        "Furniture placement blocks the clearance area required by door " + door.id(),
                        item.id()));
            }
        }
        return issues;
    }
}

