package com.naydiko.backend.geometry.model;

import java.util.UUID;

/**
 * Plain geometric representation of an opening (door/window/archway) carved
 * into a wall, at {@code offsetFromStartMm} from the wall's start node.
 */
public record OpeningGeometry(
        UUID id,
        UUID wallId,
        String type,
        double offsetFromStartMm,
        double widthMm,
        double heightMm
) {

    public boolean isDoor() {
        return "DOOR".equals(type);
    }
}

