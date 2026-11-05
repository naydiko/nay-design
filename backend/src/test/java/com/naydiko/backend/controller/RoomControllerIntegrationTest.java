package com.naydiko.backend.controller;

import com.naydiko.backend.domain.entity.Node;
import com.naydiko.backend.domain.entity.Level;
import com.naydiko.backend.domain.entity.Project;
import com.naydiko.backend.domain.entity.Room;
import com.naydiko.backend.domain.entity.User;
import com.naydiko.backend.domain.entity.Wall;
import com.naydiko.backend.domain.enums.CeilingType;
import com.naydiko.backend.domain.enums.ProjectType;
import com.naydiko.backend.domain.enums.RoomType;
import com.naydiko.backend.domain.enums.WallKind;
import com.naydiko.backend.domain.repository.LevelRepository;
import com.naydiko.backend.domain.repository.NodeRepository;
import com.naydiko.backend.domain.repository.ProjectRepository;
import com.naydiko.backend.domain.repository.RoomRepository;
import com.naydiko.backend.domain.repository.WallRepository;
import com.naydiko.backend.dto.request.CreateRoomRequest;
import com.naydiko.backend.dto.request.CreateRoomFromBoundaryRequest;
import com.naydiko.backend.dto.request.UpdateRoomRequest;
import com.naydiko.backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for the Room CRUD API ({@code /api/levels/{levelId}/rooms},
 * {@code /api/rooms/{id}}).
 */
class RoomControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private ProjectRepository projectRepository;
    @Autowired
    private LevelRepository levelRepository;
    @Autowired
    private NodeRepository nodeRepository;
    @Autowired
    private WallRepository wallRepository;
    @Autowired
    private RoomRepository roomRepository;

    @Test
    void create_returnsCreatedRoomWithLocationHeader() throws Exception {
        User owner = createActiveUser("room-caller");
        String token = bearer(tokenFor(owner));
        UUID levelId = createLevel(owner);
        CreateRoomRequest request = new CreateRoomRequest(
                "Living Room", RoomType.LIVING_ROOM, "Oak parquet", "White paint", "White paint",
                CeilingType.FLAT, new BigDecimal("2700.00"));

        mockMvc.perform(post("/api/levels/{levelId}/rooms", levelId)
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.levelId").value(levelId.toString()))
                .andExpect(jsonPath("$.name").value("Living Room"))
                .andExpect(jsonPath("$.type").value("LIVING_ROOM"))
                .andExpect(jsonPath("$.ceilingType").value("FLAT"));
    }

    @Test
    void create_doesNotAssignExistingLevelWallsToNewRoom() throws Exception {
        User owner = createActiveUser("room-caller");
        String token = bearer(tokenFor(owner));
        UUID levelId = createLevel(owner);
        createWall(levelRepository.findById(levelId).orElseThrow());

        CreateRoomRequest request = new CreateRoomRequest(
                "Unbounded Room", RoomType.OTHER, null, null, null, null, null);

        mockMvc.perform(post("/api/levels/{levelId}/rooms", levelId)
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Unbounded Room"));

        mockMvc.perform(get("/api/levels/{levelId}/geometry", levelId).header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.walls.length()").value(1))
                .andExpect(jsonPath("$.rooms.length()").value(1))
                .andExpect(jsonPath("$.roomWalls.length()").value(0));
    }

    @Test
    void createFromBoundary_persistsRoomAndRoomWallsTogether() throws Exception {
        User owner = createActiveUser("room-boundary-caller");
        String token = bearer(tokenFor(owner));
        UUID levelId = createLevel(owner);
        Level level = levelRepository.findById(levelId).orElseThrow();
        List<Wall> walls = createRectangleWalls(level, "0", "0", "4000", "2500");
        CreateRoomFromBoundaryRequest request = new CreateRoomFromBoundaryRequest(
                "Living Room", RoomType.LIVING_ROOM, walls.stream().map(Wall::getId).toList());

        String body = mockMvc.perform(post("/api/levels/{levelId}/rooms/from-boundary", levelId)
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Living Room"))
                .andReturn().getResponse().getContentAsString();

        UUID roomId = UUID.fromString(objectMapper.readTree(body).get("id").asText());
        Room room = roomRepository.findByLevelIdFetchingWalls(levelId).stream()
                .filter(candidate -> candidate.getId().equals(roomId))
                .findFirst()
                .orElseThrow();
        assertThat(room.getWalls()).extracting(Wall::getId)
                .containsExactlyInAnyOrderElementsOf(walls.stream().map(Wall::getId).toList());
    }

    @Test
    void createFromBoundary_rejectsOpenBoundaryWithoutCreatingRoom() throws Exception {
        User owner = createActiveUser("room-boundary-caller");
        String token = bearer(tokenFor(owner));
        UUID levelId = createLevel(owner);
        Level level = levelRepository.findById(levelId).orElseThrow();
        List<Wall> walls = createRectangleWalls(level, "0", "0", "4000", "2500");
        CreateRoomFromBoundaryRequest request = new CreateRoomFromBoundaryRequest(
                "Broken Room", RoomType.OTHER, walls.subList(0, 3).stream().map(Wall::getId).toList());

        mockMvc.perform(post("/api/levels/{levelId}/rooms/from-boundary", levelId)
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        assertThat(roomRepository.findByLevelId(levelId)).isEmpty();
        assertThat(wallRepository.findByLevelId(levelId)).hasSize(4);
    }

    @Test
    void create_unknownLevel_returnsNotFound() throws Exception {
        String token = bearer(createUserAndToken("room-caller"));
        CreateRoomRequest request = new CreateRoomRequest(
                "Ghost Room", RoomType.OTHER, null, null, null, null, null);

        mockMvc.perform(post("/api/levels/{levelId}/rooms", UUID.randomUUID())
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    void list_returnsRoomsForLevel() throws Exception {
        User owner = createActiveUser("room-caller");
        String token = bearer(tokenFor(owner));
        UUID levelId = createLevel(owner);
        createRoom(levelId, "Bedroom", RoomType.BEDROOM, token);
        createRoom(levelId, "Kitchen", RoomType.KITCHEN, token);

        mockMvc.perform(get("/api/levels/{levelId}/rooms", levelId).header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void get_returnsRoomById() throws Exception {
        User owner = createActiveUser("room-caller");
        String token = bearer(tokenFor(owner));
        UUID levelId = createLevel(owner);
        String roomId = createRoom(levelId, "Office", RoomType.OFFICE, token);

        mockMvc.perform(get("/api/rooms/{id}", roomId).header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(roomId))
                .andExpect(jsonPath("$.name").value("Office"));
    }

    @Test
    void get_unknownId_returnsNotFound() throws Exception {
        mockMvc.perform(get("/api/rooms/{id}", UUID.randomUUID())
                        .header("Authorization", bearer(createUserAndToken("room-caller"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void update_appliesAllFields() throws Exception {
        User owner = createActiveUser("room-caller");
        String token = bearer(tokenFor(owner));
        UUID levelId = createLevel(owner);
        String roomId = createRoom(levelId, "Original Room", RoomType.OTHER, token);

        UpdateRoomRequest update = new UpdateRoomRequest(
                "Renamed Room", RoomType.HALLWAY, "Tile", "Blue paint", "Popcorn",
                CeilingType.VAULTED, new BigDecimal("3200.00"));

        mockMvc.perform(patch("/api/rooms/{id}", roomId)
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Renamed Room"))
                .andExpect(jsonPath("$.type").value("HALLWAY"))
                .andExpect(jsonPath("$.ceilingType").value("VAULTED"));
    }

    @Test
    void delete_removesRoomSoItIsNoLongerFetchable() throws Exception {
        User owner = createActiveUser("room-caller");
        String token = bearer(tokenFor(owner));
        UUID levelId = createLevel(owner);
        String roomId = createRoom(levelId, "Doomed Room", RoomType.OTHER, token);

        mockMvc.perform(delete("/api/rooms/{id}", roomId).header("Authorization", token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/rooms/{id}", roomId).header("Authorization", token))
                .andExpect(status().isNotFound());
    }

    @Test
    void delete_removesOnlySelectedRoomAndLeavesSharedWallForOtherRoom() throws Exception {
        User owner = createActiveUser("room-caller");
        String token = bearer(tokenFor(owner));
        UUID levelId = createLevel(owner);
        Level level = levelRepository.findById(levelId).orElseThrow();
        Wall sharedWall = createWall(level);
        Room roomA = roomRepository.save(Room.builder()
                .level(level)
                .name("Room A")
                .type(RoomType.LIVING_ROOM)
                .build());
        Room roomB = roomRepository.save(Room.builder()
                .level(level)
                .name("Room B")
                .type(RoomType.BEDROOM)
                .build());
        roomA.addWall(sharedWall);
        roomB.addWall(sharedWall);
        roomRepository.saveAll(List.of(roomA, roomB));

        mockMvc.perform(delete("/api/rooms/{id}", roomA.getId()).header("Authorization", token))
                .andExpect(status().isNoContent());

        assertThat(roomRepository.existsById(roomA.getId())).isFalse();
        assertThat(wallRepository.existsById(sharedWall.getId())).isTrue();
        Room reloadedRoomB = roomRepository.findByLevelIdFetchingWalls(levelId).stream()
                .filter(room -> room.getId().equals(roomB.getId()))
                .findFirst()
                .orElseThrow();
        assertThat(reloadedRoomB.getWalls()).extracting(Wall::getId).containsExactly(sharedWall.getId());

        mockMvc.perform(get("/api/levels/{levelId}/geometry", levelId).header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.walls.length()").value(1))
                .andExpect(jsonPath("$.rooms.length()").value(1))
                .andExpect(jsonPath("$.rooms[0].id").value(roomB.getId().toString()))
                .andExpect(jsonPath("$.roomWalls.length()").value(1))
                .andExpect(jsonPath("$.roomWalls[0].roomId").value(roomB.getId().toString()))
                .andExpect(jsonPath("$.roomWalls[0].wallId").value(sharedWall.getId().toString()));
    }

    @Test
    void createAndDeleteAdjacentRooms_reusesSharedWallAndPreservesLevelGeometry() throws Exception {
        User owner = createActiveUser("adjacent-room-caller");
        String token = bearer(tokenFor(owner));
        UUID levelId = createLevel(owner);
        Level level = levelRepository.findById(levelId).orElseThrow();
        Node a = createNode(level, "0", "0");
        Node b = createNode(level, "4000", "0");
        Node c = createNode(level, "7000", "0");
        Node d = createNode(level, "0", "2500");
        Node e = createNode(level, "4000", "2500");
        Node f = createNode(level, "7000", "2500");
        Wall ab = wallRepository.save(createWall(level, a, b));
        Wall bc = wallRepository.save(createWall(level, b, c));
        Wall cf = wallRepository.save(createWall(level, c, f));
        Wall fe = wallRepository.save(createWall(level, f, e));
        Wall ed = wallRepository.save(createWall(level, e, d));
        Wall da = wallRepository.save(createWall(level, d, a));
        Wall sharedBe = wallRepository.save(createWall(level, b, e));

        UUID livingId = createRoomFromBoundary(levelId, "Living", RoomType.LIVING_ROOM,
                List.of(ab, sharedBe, ed, da), token);
        UUID kitchenId = createRoomFromBoundary(levelId, "Kitchen", RoomType.KITCHEN,
                List.of(bc, cf, fe, sharedBe), token);

        List<Room> created = roomRepository.findByLevelIdFetchingWalls(levelId);
        assertThat(created).hasSize(2);
        assertThat(created.stream().filter(room -> room.getWalls().contains(sharedBe))).hasSize(2);
        assertThat(wallRepository.findByLevelId(levelId)).hasSize(7);

        mockMvc.perform(delete("/api/rooms/{id}", livingId).header("Authorization", token))
                .andExpect(status().isNoContent());

        assertThat(roomRepository.existsById(livingId)).isFalse();
        assertThat(wallRepository.findByLevelId(levelId)).hasSize(7);
        Room kitchen = roomRepository.findByLevelIdFetchingWalls(levelId).stream()
                .filter(room -> room.getId().equals(kitchenId))
                .findFirst()
                .orElseThrow();
        assertThat(kitchen.getWalls()).extracting(Wall::getId)
                .containsExactlyInAnyOrder(bc.getId(), cf.getId(), fe.getId(), sharedBe.getId());
    }

    private UUID createLevel(User owner) {
        Project project = projectRepository.save(Project.builder()
                .owner(owner)
                .name("Room Test Project")
                .projectType(ProjectType.RESIDENTIAL)
                .build());
        Level level = levelRepository.save(Level.builder()
                .project(project)
                .name("Room Test Level")
                .build());
        return level.getId();
    }

    private Wall createWall(Level level) {
        Node start = nodeRepository.save(Node.builder()
                .level(level)
                .xMm(BigDecimal.ZERO)
                .yMm(BigDecimal.ZERO)
                .zMm(BigDecimal.ZERO)
                .build());
        Node end = nodeRepository.save(Node.builder()
                .level(level)
                .xMm(new BigDecimal("4000"))
                .yMm(BigDecimal.ZERO)
                .zMm(BigDecimal.ZERO)
                .build());
        return wallRepository.save(Wall.builder()
                .level(level)
                .startNode(start)
                .endNode(end)
                .thicknessMm(new BigDecimal("150"))
                .heightMm(new BigDecimal("2700"))
                .kind(WallKind.INTERIOR)
                .build());
    }

    private List<Wall> createRectangleWalls(Level level, String minX, String minY, String maxX, String maxY) {
        Node a = createNode(level, minX, minY);
        Node b = createNode(level, maxX, minY);
        Node c = createNode(level, maxX, maxY);
        Node d = createNode(level, minX, maxY);
        return wallRepository.saveAll(List.of(
                createWall(level, a, b),
                createWall(level, b, c),
                createWall(level, c, d),
                createWall(level, d, a)));
    }

    private Node createNode(Level level, String xMm, String yMm) {
        return nodeRepository.save(Node.builder()
                .level(level)
                .xMm(new BigDecimal(xMm))
                .yMm(new BigDecimal(yMm))
                .zMm(BigDecimal.ZERO)
                .build());
    }

    private Wall createWall(Level level, Node start, Node end) {
        return Wall.builder()
                .level(level)
                .startNode(start)
                .endNode(end)
                .thicknessMm(new BigDecimal("150"))
                .heightMm(new BigDecimal("2700"))
                .kind(WallKind.INTERIOR)
                .build();
    }

    private String createRoom(UUID levelId, String name, RoomType type, String bearerToken) throws Exception {
        CreateRoomRequest request = new CreateRoomRequest(name, type, null, null, null, null, null);
        String body = mockMvc.perform(post("/api/levels/{levelId}/rooms", levelId)
                        .header("Authorization", bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asText();
    }

    private UUID createRoomFromBoundary(
            UUID levelId, String name, RoomType type, List<Wall> walls, String bearerToken) throws Exception {
        CreateRoomFromBoundaryRequest request = new CreateRoomFromBoundaryRequest(
                name, type, walls.stream().map(Wall::getId).toList());
        String body = mockMvc.perform(post("/api/levels/{levelId}/rooms/from-boundary", levelId)
                        .header("Authorization", bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(body).get("id").asText());
    }
}

