package com.naydiko.backend.service;

import com.naydiko.backend.domain.entity.Level;
import com.naydiko.backend.domain.entity.Node;
import com.naydiko.backend.domain.entity.Opening;
import com.naydiko.backend.domain.entity.Room;
import com.naydiko.backend.domain.entity.Wall;
import com.naydiko.backend.domain.repository.LevelRepository;
import com.naydiko.backend.domain.repository.NodeRepository;
import com.naydiko.backend.domain.repository.OpeningRepository;
import com.naydiko.backend.domain.repository.RoomRepository;
import com.naydiko.backend.domain.repository.WallRepository;
import com.naydiko.backend.dto.request.LevelGeometryRequest;
import com.naydiko.backend.dto.request.NodeRequest;
import com.naydiko.backend.dto.request.OpeningRequest;
import com.naydiko.backend.dto.request.RoomGeometryRequest;
import com.naydiko.backend.dto.request.RoomWallRequest;
import com.naydiko.backend.dto.request.WallRequest;
import com.naydiko.backend.dto.response.LevelGeometryResponse;
import com.naydiko.backend.dto.response.NodeResponse;
import com.naydiko.backend.dto.response.OpeningResponse;
import com.naydiko.backend.dto.response.RoomGeometryResponse;
import com.naydiko.backend.dto.response.RoomWallResponse;
import com.naydiko.backend.dto.response.WallResponse;
import com.naydiko.backend.exception.GeometryValidationException;
import com.naydiko.backend.exception.ResourceNotFoundException;
import com.naydiko.backend.geometry.GeometryEngine;
import com.naydiko.backend.geometry.model.GeometryValidationIssue;
import com.naydiko.backend.geometry.model.OpeningGeometry;
import com.naydiko.backend.geometry.model.RoomBoundary;
import com.naydiko.backend.geometry.model.WallGeometry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Business logic for loading and saving the complete geometry document
 * (nodes, walls, openings, rooms, and room-wall borders) of a {@link Level}.
 *
 * <p>This treats the entire geometry as one logical document from the
 * frontend's perspective: the canvas edits geometry locally and saves the
 * complete state in a single request, rather than issuing individual CRUD
 * calls per node/wall/opening/room. Structural geometry validation
 * (wall length/thickness/height, opening bounds, room closure) is delegated
 * to the {@link GeometryEngine}; error-level findings abort the save (the
 * transaction is rolled back) via {@link GeometryValidationException}. No
 * collision detection, room-polygon derivation, or snapping is performed
 * here — see {@link RoomService} for furniture-related Geometry Engine checks.
 */
@Service
@Transactional(readOnly = true)
public class LevelGeometryService {

    private static final Logger log = LoggerFactory.getLogger(LevelGeometryService.class);

    private final LevelRepository levelRepository;
    private final NodeRepository nodeRepository;
    private final WallRepository wallRepository;
    private final OpeningRepository openingRepository;
    private final RoomRepository roomRepository;
    private final GeometryEngine geometryEngine;

    public LevelGeometryService(LevelRepository levelRepository,
                                 NodeRepository nodeRepository,
                                 WallRepository wallRepository,
                                 OpeningRepository openingRepository,
                                 RoomRepository roomRepository,
                                 GeometryEngine geometryEngine) {
        this.levelRepository = levelRepository;
        this.nodeRepository = nodeRepository;
        this.wallRepository = wallRepository;
        this.openingRepository = openingRepository;
        this.roomRepository = roomRepository;
        this.geometryEngine = geometryEngine;
    }

    public LevelGeometryResponse getGeometry(UUID levelId) {
        findLevelOrThrow(levelId);

        List<Node> nodes = nodeRepository.findByLevelId(levelId);
        List<Wall> walls = wallRepository.findByLevelId(levelId);
        List<Opening> openings = openingRepository.findByWallLevelId(levelId);
        List<Room> rooms = roomRepository.findByLevelId(levelId);

        List<RoomWallResponse> roomWalls = rooms.stream()
                .flatMap(room -> room.getWalls().stream()
                        .map(wall -> new RoomWallResponse(room.getId(), wall.getId())))
                .toList();

        return new LevelGeometryResponse(
                levelId,
                nodes.stream().map(LevelGeometryService::toNodeResponse).toList(),
                walls.stream().map(LevelGeometryService::toWallResponse).toList(),
                openings.stream().map(LevelGeometryService::toOpeningResponse).toList(),
                rooms.stream().map(LevelGeometryService::toRoomResponse).toList(),
                roomWalls
        );
    }

    @Transactional
    public LevelGeometryResponse saveGeometry(UUID levelId, LevelGeometryRequest request) {
        Level level = findLevelOrThrow(levelId);

        Map<UUID, Node> existingNodes = indexById(nodeRepository.findByLevelId(levelId), Node::getId);
        Map<UUID, Wall> existingWalls = indexById(wallRepository.findByLevelId(levelId), Wall::getId);
        Map<UUID, Opening> existingOpenings = indexById(openingRepository.findByWallLevelId(levelId), Opening::getId);
        Map<UUID, Room> existingRooms = indexById(roomRepository.findByLevelId(levelId), Room::getId);

        // 1. Nodes: upsert.
        Map<UUID, Node> nodeById = new HashMap<>();
        for (NodeRequest nr : request.nodes()) {
            Node node = resolveOrThrow(nr.id(), existingNodes, "Node");
            if (node == null) {
                node = Node.builder().level(level).build();
            }
            node.setXMm(nr.xMm());
            node.setYMm(nr.yMm());
            node.setZMm(nr.zMm());
            node = nodeRepository.save(node);
            nodeById.put(node.getId(), node);
        }
        nodeRepository.flush();

        // 2. Walls: upsert, resolving node references from the batch above.
        Map<UUID, Wall> wallById = new HashMap<>();
        for (WallRequest wr : request.walls()) {
            Wall wall = resolveOrThrow(wr.id(), existingWalls, "Wall");
            Node startNode = nodeById.get(wr.startNodeId());
            Node endNode = nodeById.get(wr.endNodeId());
            if (startNode == null || endNode == null) {
                throw new IllegalArgumentException(
                        "Wall references a node that is not part of the submitted geometry for level " + levelId);
            }
            if (wall == null) {
                wall = Wall.builder().level(level).build();
            }
            wall.setStartNode(startNode);
            wall.setEndNode(endNode);
            wall.setThicknessMm(wr.thicknessMm());
            wall.setHeightMm(wr.heightMm());
            wall.setKind(wr.kind());
            wall = wallRepository.save(wall);
            wallById.put(wall.getId(), wall);
        }
        wallRepository.flush();

        // 3. Openings: upsert, resolving wall references from the batch above.
        Map<UUID, Opening> openingById = new HashMap<>();
        for (OpeningRequest or : request.openings()) {
            Opening opening = resolveOrThrow(or.id(), existingOpenings, "Opening");
            Wall wall = wallById.get(or.wallId());
            if (wall == null) {
                throw new IllegalArgumentException(
                        "Opening references a wall that is not part of the submitted geometry for level " + levelId);
            }
            if (opening == null) {
                opening = Opening.builder().build();
            }
            opening.setWall(wall);
            opening.setType(or.type());
            opening.setOffsetFromStartMm(or.offsetFromStartMm());
            opening.setWidthMm(or.widthMm());
            opening.setHeightMm(or.heightMm());
            opening.setSillHeightMm(or.sillHeightMm() != null ? or.sillHeightMm() : BigDecimal.ZERO);
            opening.setDirection(or.direction());
            opening.setSwing(or.swing());
            opening = openingRepository.save(opening);
            openingById.put(opening.getId(), opening);
        }
        openingRepository.flush();

        // Geometry Engine: validate structural wall/opening geometry before
        // committing anything further. Error-severity issues abort the save
        // (an unchecked exception rolls back this whole transaction, so
        // nothing saved above is persisted either).
        validateWallsAndOpenings(wallById, openingById);

        // Remove openings that are no longer present in the submitted geometry.
        List<Opening> openingsToRemove = existingOpenings.values().stream()
                .filter(o -> !openingById.containsKey(o.getId()))
                .toList();
        openingRepository.deleteAll(openingsToRemove);
        openingRepository.flush();

        // 4. Rooms: upsert geometry-relevant fields only (name, type).
        Map<UUID, Room> roomById = new HashMap<>();
        for (RoomGeometryRequest rr : request.rooms()) {
            Room room = resolveOrThrow(rr.id(), existingRooms, "Room");
            if (room == null) {
                room = Room.builder().level(level).name(rr.name()).type(rr.type()).build();
            } else {
                room.setName(rr.name());
                room.setType(rr.type());
            }
            room = roomRepository.save(room);
            roomById.put(room.getId(), room);
        }
        roomRepository.flush();

        // 5. Room-wall borders: reconcile each room's wall associations.
        Map<UUID, Set<Wall>> wallsByRoom = new HashMap<>();
        for (RoomWallRequest rw : request.roomWalls()) {
            Room room = roomById.get(rw.roomId());
            Wall wall = wallById.get(rw.wallId());
            if (room == null || wall == null) {
                throw new IllegalArgumentException(
                        "Room-wall association references a room or wall that is not part of the submitted geometry for level "
                                + levelId);
            }
            wallsByRoom.computeIfAbsent(rw.roomId(), key -> new HashSet<>()).add(wall);
        }
        for (Room room : roomById.values()) {
            room.setWalls(wallsByRoom.getOrDefault(room.getId(), new HashSet<>()));
        }
        roomRepository.flush();

        // Geometry Engine: room-closure is advisory only (many valid
        // in-progress rooms are not yet fully enclosed), so we log it rather
        // than block the save.
        logRoomClosureWarnings(roomById.values());

        // Remove rooms that are no longer present in the submitted geometry.
        List<Room> roomsToRemove = existingRooms.values().stream()
                .filter(r -> !roomById.containsKey(r.getId()))
                .toList();
        roomRepository.deleteAll(roomsToRemove);
        roomRepository.flush();

        // Remove walls that are no longer present in the submitted geometry.
        List<Wall> wallsToRemove = existingWalls.values().stream()
                .filter(w -> !wallById.containsKey(w.getId()))
                .toList();
        wallRepository.deleteAll(wallsToRemove);
        wallRepository.flush();

        // Remove nodes that are no longer present in the submitted geometry.
        // Safe at this point: any wall still referencing a node is, by
        // construction, in wallById and therefore only ever points at nodes in nodeById.
        List<Node> nodesToRemove = existingNodes.values().stream()
                .filter(n -> !nodeById.containsKey(n.getId()))
                .toList();
        nodeRepository.deleteAll(nodesToRemove);
        nodeRepository.flush();

        return getGeometry(levelId);
    }

    /**
     * Runs the Geometry Engine's structural validation over the walls and
     * openings just upserted for this save, throwing
     * {@link GeometryValidationException} (aborting/rolling back the save)
     * if any error-severity issue is found.
     */
    private void validateWallsAndOpenings(Map<UUID, Wall> wallById, Map<UUID, Opening> openingById) {
        List<WallGeometry> wallGeometries = wallById.values().stream()
                .map(LevelGeometryService::toWallGeometry)
                .toList();
        Map<UUID, Double> wallLengthsById = wallGeometries.stream()
                .collect(Collectors.toMap(WallGeometry::id, WallGeometry::lengthMm));

        List<OpeningGeometry> openingGeometries = openingById.values().stream()
                .map(LevelGeometryService::toOpeningGeometry)
                .toList();

        List<GeometryValidationIssue> issues = new ArrayList<>();
        issues.addAll(geometryEngine.validateWalls(wallGeometries).issues());
        issues.addAll(geometryEngine.validateOpenings(openingGeometries, wallLengthsById).issues());

        if (!issues.isEmpty()) {
            log.debug("Geometry Engine found {} issue(s) while saving level geometry", issues.size());
        }

        List<GeometryValidationIssue> errors = issues.stream()
                .filter(i -> i.severity() == com.naydiko.backend.geometry.model.GeometrySeverity.ERROR)
                .toList();
        if (!errors.isEmpty()) {
            throw new GeometryValidationException(errors);
        }
    }

    /**
     * Logs (but does not block on) room-closure findings: whether each
     * room's currently-associated walls form a closed boundary.
     */
    private void logRoomClosureWarnings(Iterable<Room> rooms) {
        for (Room room : rooms) {
            List<WallGeometry> walls = room.getWalls().stream()
                    .map(LevelGeometryService::toWallGeometry)
                    .toList();
            RoomBoundary boundary = new RoomBoundary(room.getId(), walls);
            for (GeometryValidationIssue issue : geometryEngine.analyzeRoom(boundary).issues()) {
                log.debug("Geometry Engine: room {} - {}: {}", room.getId(), issue.code(), issue.message());
            }
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

    private static OpeningGeometry toOpeningGeometry(Opening opening) {
        return new OpeningGeometry(
                opening.getId(),
                opening.getWall().getId(),
                opening.getType().name(),
                opening.getOffsetFromStartMm().doubleValue(),
                opening.getWidthMm().doubleValue(),
                opening.getHeightMm().doubleValue()
        );
    }

    /**
     * Resolves an existing entity by id (throwing if a client-supplied id
     * does not correspond to an entity of this level), or returns
     * {@code null} to signal that a new entity should be created.
     */
    private static <T> T resolveOrThrow(UUID id, Map<UUID, T> existingById, String entityName) {
        if (id == null) {
            return null;
        }
        T existing = existingById.get(id);
        if (existing == null) {
            throw new ResourceNotFoundException(entityName + " not found: " + id);
        }
        return existing;
    }

    private static <T> Map<UUID, T> indexById(List<T> items, Function<T, UUID> idFn) {
        return items.stream().collect(Collectors.toMap(idFn, Function.identity()));
    }

    private Level findLevelOrThrow(UUID levelId) {
        return levelRepository.findById(levelId)
                .orElseThrow(() -> new ResourceNotFoundException("Level not found: " + levelId));
    }

    private static NodeResponse toNodeResponse(Node node) {
        return new NodeResponse(node.getId(), node.getXMm(), node.getYMm(), node.getZMm());
    }

    private static WallResponse toWallResponse(Wall wall) {
        return new WallResponse(
                wall.getId(),
                wall.getStartNode().getId(),
                wall.getEndNode().getId(),
                wall.getThicknessMm(),
                wall.getHeightMm(),
                wall.getKind()
        );
    }

    private static OpeningResponse toOpeningResponse(Opening opening) {
        return new OpeningResponse(
                opening.getId(),
                opening.getWall().getId(),
                opening.getType(),
                opening.getOffsetFromStartMm(),
                opening.getWidthMm(),
                opening.getHeightMm(),
                opening.getSillHeightMm(),
                opening.getDirection(),
                opening.getSwing()
        );
    }

    private static RoomGeometryResponse toRoomResponse(Room room) {
        return new RoomGeometryResponse(room.getId(), room.getName(), room.getType());
    }
}




