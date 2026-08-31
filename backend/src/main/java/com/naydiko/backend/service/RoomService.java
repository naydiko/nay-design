package com.naydiko.backend.service;

import com.naydiko.backend.domain.entity.FurniturePlacement;
import com.naydiko.backend.domain.entity.Level;
import com.naydiko.backend.domain.entity.Product;
import com.naydiko.backend.domain.entity.Room;
import com.naydiko.backend.domain.entity.Wall;
import com.naydiko.backend.domain.enums.OpeningType;
import com.naydiko.backend.domain.repository.FurniturePlacementRepository;
import com.naydiko.backend.domain.repository.LevelRepository;
import com.naydiko.backend.domain.repository.ProductRepository;
import com.naydiko.backend.domain.repository.RoomRepository;
import com.naydiko.backend.dto.request.CreateRoomRequest;
import com.naydiko.backend.dto.request.FurniturePlacementRequest;
import com.naydiko.backend.dto.request.UpdateRoomRequest;
import com.naydiko.backend.dto.response.FurniturePlacementResponse;
import com.naydiko.backend.dto.response.RoomResponse;
import com.naydiko.backend.exception.ResourceNotFoundException;
import com.naydiko.backend.geometry.GeometryEngine;
import com.naydiko.backend.geometry.model.BoundingBox2D;
import com.naydiko.backend.geometry.model.FurnitureGeometry;
import com.naydiko.backend.geometry.model.GeometryValidationIssue;
import com.naydiko.backend.geometry.model.OpeningGeometry;
import com.naydiko.backend.geometry.model.RoomBoundary;
import com.naydiko.backend.geometry.model.WallGeometry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Business logic for managing {@link Room}s within a {@link Level}, including
 * the furniture placements within each room.
 *
 * <p>Furniture placements are validated (room fit, wall intersections,
 * furniture-furniture intersections, door clearance) via the
 * {@link GeometryEngine} on every save. Stage 1 does not prevent overlapping
 * placements — the canvas allows free positioning — so these findings are
 * logged as warnings rather than rejected; see {@link LevelGeometryService}
 * for the (blocking) structural wall/opening validation.
 */
@Service
@Transactional(readOnly = true)
public class RoomService {

    private static final Logger log = LoggerFactory.getLogger(RoomService.class);

    private final RoomRepository roomRepository;
    private final LevelRepository levelRepository;
    private final ProductRepository productRepository;
    private final FurniturePlacementRepository furniturePlacementRepository;
    private final GeometryEngine geometryEngine;
    private final double doorClearanceMm;

    public RoomService(RoomRepository roomRepository,
                        LevelRepository levelRepository,
                        ProductRepository productRepository,
                        FurniturePlacementRepository furniturePlacementRepository,
                        GeometryEngine geometryEngine,
                        @Value("${app.geometry.door-clearance-mm:700}") double doorClearanceMm) {
        this.roomRepository = roomRepository;
        this.levelRepository = levelRepository;
        this.productRepository = productRepository;
        this.furniturePlacementRepository = furniturePlacementRepository;
        this.geometryEngine = geometryEngine;
        this.doorClearanceMm = doorClearanceMm;
    }

    @Transactional
    public RoomResponse createRoom(UUID levelId, CreateRoomRequest request) {
        Level level = findLevelOrThrow(levelId);

        Room room = Room.builder()
                .level(level)
                .name(request.name())
                .type(request.type())
                .floorFinish(request.floorFinish())
                .wallFinish(request.wallFinish())
                .ceilingFinish(request.ceilingFinish())
                .ceilingType(request.ceilingType())
                .ceilingHeightMm(request.ceilingHeightMm())
                .build();

        return toResponse(roomRepository.save(room));
    }

    @Transactional
    public RoomResponse updateRoom(UUID id, UpdateRoomRequest request) {
        Room room = findRoomOrThrow(id);

        room.setName(request.name());
        room.setType(request.type());
        room.setFloorFinish(request.floorFinish());
        room.setWallFinish(request.wallFinish());
        room.setCeilingFinish(request.ceilingFinish());
        room.setCeilingType(request.ceilingType());
        room.setCeilingHeightMm(request.ceilingHeightMm());

        return toResponse(room);
    }

    public RoomResponse getRoom(UUID id) {
        return toResponse(findRoomOrThrow(id));
    }

    public List<RoomResponse> listRoomsByLevel(UUID levelId) {
        return roomRepository.findByLevelId(levelId).stream()
                .map(RoomService::toResponse)
                .toList();
    }

    @Transactional
    public void deleteRoom(UUID id) {
        Room room = findRoomOrThrow(id);
        roomRepository.delete(room);
    }

    public List<FurniturePlacementResponse> getPlacements(UUID roomId) {
        findRoomOrThrow(roomId);
        return furniturePlacementRepository.findByRoomId(roomId).stream()
                .map(RoomService::toPlacementResponse)
                .toList();
    }

    @Transactional
    public List<FurniturePlacementResponse> savePlacements(UUID roomId, List<FurniturePlacementRequest> requests) {
        Room room = findRoomOrThrow(roomId);

        Map<UUID, FurniturePlacement> existingPlacements = furniturePlacementRepository.findByRoomId(roomId).stream()
                .collect(Collectors.toMap(FurniturePlacement::getId, Function.identity()));

        List<FurniturePlacement> upserted = new ArrayList<>();
        for (FurniturePlacementRequest request : requests) {
            FurniturePlacement placement;
            if (request.id() != null) {
                placement = existingPlacements.get(request.id());
                if (placement == null) {
                    throw new ResourceNotFoundException(
                            "Furniture placement not found: " + request.id() + " for room " + roomId);
                }
            } else {
                placement = FurniturePlacement.builder().room(room).build();
            }

            placement.setProduct(findProductOrThrow(request.productId()));
            placement.setXMm(request.xMm());
            placement.setYMm(request.yMm());
            placement.setZMm(request.zMm());
            placement.setRotationAngle(request.rotationAngle());
            placement.setScale(request.scale());
            placement.setLocked(request.locked());

            upserted.add(placement);
        }

        List<FurniturePlacement> saved = furniturePlacementRepository.saveAll(upserted);
        Set<UUID> keptIds = saved.stream().map(FurniturePlacement::getId).collect(Collectors.toSet());

        List<FurniturePlacement> toRemove = existingPlacements.values().stream()
                .filter(placement -> !keptIds.contains(placement.getId()))
                .toList();
        furniturePlacementRepository.deleteAll(toRemove);

        // Geometry Engine: advisory-only for Stage 1 (fit/intersections/door
        // clearance are logged, not blocking — the canvas does not yet
        // prevent free placement/overlap).
        validateFurnitureGeometry(room, saved);

        return saved.stream()
                .map(RoomService::toPlacementResponse)
                .toList();
    }

    /**
     * Runs the Geometry Engine over a room's freshly-saved furniture layout:
     * missing/invalid product dimensions, room fit, wall intersections,
     * furniture-furniture intersections, and door clearance. All findings
     * are logged (Stage 1 does not block saves on spatial overlap).
     */
    private void validateFurnitureGeometry(Room room, List<FurniturePlacement> placements) {
        List<GeometryValidationIssue> issues = new ArrayList<>();

        List<FurnitureGeometry> furnitureGeometries = new ArrayList<>();
        for (FurniturePlacement placement : placements) {
            Product product = placement.getProduct();
            Double widthMm = product.getWidthMm() != null ? product.getWidthMm().doubleValue() : null;
            Double depthMm = product.getDepthMm() != null ? product.getDepthMm().doubleValue() : null;

            issues.addAll(com.naydiko.backend.geometry.FurnitureGeometryValidator.validateHasDimensions(
                    placement.getId(), widthMm, depthMm));
            if (widthMm == null || depthMm == null || widthMm <= 0 || depthMm <= 0) {
                continue;
            }

            furnitureGeometries.add(new FurnitureGeometry(
                    placement.getId(),
                    product.getId(),
                    placement.getXMm().doubleValue(),
                    placement.getYMm().doubleValue(),
                    widthMm,
                    depthMm,
                    placement.getRotationAngle().doubleValue(),
                    placement.getScale().doubleValue(),
                    placement.isLocked()
            ));
        }

        List<WallGeometry> roomWalls = room.getWalls().stream()
                .map(RoomService::toWallGeometry)
                .toList();
        BoundingBox2D roomBoundingBox = roomWalls.isEmpty()
                ? null
                : com.naydiko.backend.geometry.RoomGeometryCalculator.boundingBox(new RoomBoundary(room.getId(), roomWalls));

        issues.addAll(geometryEngine.validateFurniture(furnitureGeometries, roomBoundingBox, roomWalls).issues());

        List<OpeningGeometry> doors = room.getWalls().stream()
                .flatMap(wall -> wall.getOpenings().stream())
                .filter(opening -> opening.getType() == OpeningType.DOOR)
                .map(RoomService::toOpeningGeometry)
                .toList();
        Map<UUID, WallGeometry> wallsById = roomWalls.stream()
                .collect(Collectors.toMap(WallGeometry::id, Function.identity()));
        issues.addAll(geometryEngine.validateDoorClearances(doors, wallsById, furnitureGeometries, doorClearanceMm).issues());

        for (GeometryValidationIssue issue : issues) {
            log.debug("Geometry Engine: room {} - {}: {}", room.getId(), issue.code(), issue.message());
        }
    }

    private static WallGeometry toWallGeometry(Wall wall) {
        return new WallGeometry(
                wall.getId(),
                wall.getStartNode().getId(),
                wall.getEndNode().getId(),
                wall.getStartNode().getXMm().doubleValue(),
                wall.getStartNode().getYMm().doubleValue(),
                wall.getEndNode().getXMm().doubleValue(),
                wall.getEndNode().getYMm().doubleValue(),
                wall.getThicknessMm().doubleValue(),
                wall.getHeightMm().doubleValue()
        );
    }

    private static OpeningGeometry toOpeningGeometry(com.naydiko.backend.domain.entity.Opening opening) {
        return new OpeningGeometry(
                opening.getId(),
                opening.getWall().getId(),
                opening.getType().name(),
                opening.getOffsetFromStartMm().doubleValue(),
                opening.getWidthMm().doubleValue(),
                opening.getHeightMm().doubleValue()
        );
    }

    private Room findRoomOrThrow(UUID id) {
        return roomRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Room not found: " + id));
    }

    private Level findLevelOrThrow(UUID levelId) {
        return levelRepository.findById(levelId)
                .orElseThrow(() -> new ResourceNotFoundException("Level not found: " + levelId));
    }

    private Product findProductOrThrow(UUID productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + productId));
    }

    private static RoomResponse toResponse(Room room) {
        return new RoomResponse(
                room.getId(),
                room.getLevel().getId(),
                room.getName(),
                room.getType(),
                room.getFloorFinish(),
                room.getWallFinish(),
                room.getCeilingFinish(),
                room.getCeilingType(),
                room.getCeilingHeightMm(),
                room.getCreatedAt(),
                room.getUpdatedAt()
        );
    }

    private static FurniturePlacementResponse toPlacementResponse(FurniturePlacement placement) {
        return new FurniturePlacementResponse(
                placement.getId(),
                placement.getRoom().getId(),
                placement.getProduct().getId(),
                placement.getXMm(),
                placement.getYMm(),
                placement.getZMm(),
                placement.getRotationAngle(),
                placement.getScale(),
                placement.isLocked(),
                placement.getCreatedAt(),
                placement.getUpdatedAt()
        );
    }
}

