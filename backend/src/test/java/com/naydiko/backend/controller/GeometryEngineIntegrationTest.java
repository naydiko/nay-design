package com.naydiko.backend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.naydiko.backend.domain.entity.Level;
import com.naydiko.backend.domain.entity.Product;
import com.naydiko.backend.domain.entity.Project;
import com.naydiko.backend.domain.entity.User;
import com.naydiko.backend.domain.entity.Vendor;
import com.naydiko.backend.domain.enums.OpeningType;
import com.naydiko.backend.domain.enums.ProductStatus;
import com.naydiko.backend.domain.enums.ProjectType;
import com.naydiko.backend.domain.enums.RoomType;
import com.naydiko.backend.domain.enums.VendorStatus;
import com.naydiko.backend.domain.enums.WallKind;
import com.naydiko.backend.domain.repository.LevelRepository;
import com.naydiko.backend.domain.repository.ProductRepository;
import com.naydiko.backend.domain.repository.ProjectRepository;
import com.naydiko.backend.domain.repository.VendorRepository;
import com.naydiko.backend.dto.request.FurniturePlacementRequest;
import com.naydiko.backend.dto.request.LevelGeometryRequest;
import com.naydiko.backend.dto.request.NodeRequest;
import com.naydiko.backend.dto.request.OpeningRequest;
import com.naydiko.backend.dto.request.RoomGeometryRequest;
import com.naydiko.backend.dto.request.RoomWallRequest;
import com.naydiko.backend.dto.request.WallRequest;
import com.naydiko.backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end (API-level) tests of the Geometry Engine's Stage 1 validation
 * matrix, exercised through the real {@code /api/levels/{id}/geometry} and
 * {@code /api/rooms/{id}/placements} endpoints rather than calling the
 * engine directly (see {@code GeometryEngineTest} for focused unit tests of
 * the same rules) — this confirms the wiring from HTTP request through
 * {@code LevelGeometryService}/{@code RoomService} into the engine, and
 * back out as structured {@code issues} in the response body.
 */
class GeometryEngineIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private ProjectRepository projectRepository;
    @Autowired
    private LevelRepository levelRepository;
    @Autowired
    private VendorRepository vendorRepository;
    @Autowired
    private ProductRepository productRepository;

    @Test
    void validRoom_closedRectangleHasNoIssues() throws Exception {
        UUID levelId = createLevel();
        String token = bearer(createUserAndToken("engine-caller"));

        JsonNode result = saveClosedRoom(levelId, token);

        assertThat(result.get("issues")).isEmpty();
        assertThat(result.get("rooms")).hasSize(1);
    }

    @Test
    void invalidWall_zeroLengthIsRejected() throws Exception {
        UUID levelId = createLevel();
        String token = bearer(createUserAndToken("engine-caller"));

        NodeRequest n1 = new NodeRequest(null, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        NodeRequest n2 = new NodeRequest(null, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        JsonNode nodesOnly = readJson(putGeometry(levelId, token,
                new LevelGeometryRequest(List.of(n1, n2), List.of(), List.of(), List.of(), List.of()))
                .andExpect(status().isOk()));
        UUID nodeA = idOf(nodesOnly.get("nodes").get(0));
        UUID nodeB = idOf(nodesOnly.get("nodes").get(1));

        WallRequest zeroLength = new WallRequest(
                null, nodeA, nodeB, new BigDecimal("100"), new BigDecimal("2700"), WallKind.INTERIOR);

        putGeometry(levelId, token, new LevelGeometryRequest(
                        toNodeRequests(nodesOnly.get("nodes")), List.of(zeroLength), List.of(), List.of(), List.of()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.issues[*].code").value(hasItem("WALL_ZERO_LENGTH")));
    }

    @Test
    void openingOutsideWall_isRejected() throws Exception {
        UUID levelId = createLevel();
        String token = bearer(createUserAndToken("engine-caller"));

        // A single 1000mm wall; a 900mm-wide door at offset 500 doesn't fit
        // (500 + 900 = 1400 > 1000).
        JsonNode withWall = saveSingleWall(levelId, token, 1000);
        UUID wallId = idOf(withWall.get("walls").get(0));

        OpeningRequest door = new OpeningRequest(
                null, wallId, OpeningType.DOOR, new BigDecimal("500"), new BigDecimal("900"),
                new BigDecimal("2100"), null, null, null);

        putGeometry(levelId, token, new LevelGeometryRequest(
                        toNodeRequests(withWall.get("nodes")), toWallRequests(withWall.get("walls")),
                        List.of(door), List.of(), List.of()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.issues[*].code").value(hasItem("OPENING_OUT_OF_BOUNDS")));
    }

    @Test
    void furnitureOutsideRoom_isReportedAsWarningButSaveSucceeds() throws Exception {
        UUID levelId = createLevel();
        String token = bearer(createUserAndToken("engine-caller"));
        UUID roomId = idOf(saveClosedRoom(levelId, token).get("rooms").get(0));
        UUID productId = createProduct("500x500 stool", "500.00", "500.00").getId();

        JsonNode result = savePlacements(roomId, token, List.of(new FurniturePlacementRequest(
                null, productId, new BigDecimal("9000"), new BigDecimal("9000"), BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ONE, false)));

        assertThat(codesOf(result.get("issues"))).contains("FURNITURE_OUTSIDE_ROOM");
    }

    @Test
    void furnitureIntersectingWall_isReportedAsWarningButSaveSucceeds() throws Exception {
        UUID levelId = createLevel();
        String token = bearer(createUserAndToken("engine-caller"));
        UUID roomId = idOf(saveClosedRoom(levelId, token).get("rooms").get(0));
        // Large item straddling the right-hand wall (a vertical wall at x=4000).
        UUID productId = createProduct("wall-hugging cabinet", "600.00", "600.00").getId();

        JsonNode result = savePlacements(roomId, token, List.of(new FurniturePlacementRequest(
                null, productId, new BigDecimal("4000"), new BigDecimal("1500"), BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ONE, false)));

        assertThat(codesOf(result.get("issues"))).contains("FURNITURE_INTERSECTS_WALL");
    }

    @Test
    void furnitureIntersectingFurniture_isReportedAsWarningButSaveSucceeds() throws Exception {
        UUID levelId = createLevel();
        String token = bearer(createUserAndToken("engine-caller"));
        UUID roomId = idOf(saveClosedRoom(levelId, token).get("rooms").get(0));
        UUID productId = createProduct("700x700 ottoman", "700.00", "700.00").getId();

        JsonNode result = savePlacements(roomId, token, List.of(
                new FurniturePlacementRequest(null, productId, new BigDecimal("1000"), new BigDecimal("1000"),
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ONE, false),
                new FurniturePlacementRequest(null, productId, new BigDecimal("1300"), new BigDecimal("1000"),
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ONE, false)));

        assertThat(codesOf(result.get("issues"))).contains("FURNITURE_INTERSECTS_FURNITURE");
    }

    @Test
    void furnitureBlockingDoor_isReportedAsWarningButSaveSucceeds() throws Exception {
        UUID levelId = createLevel();
        String token = bearer(createUserAndToken("engine-caller"));

        JsonNode withWalls = saveRectangleWalls(levelId, token);
        UUID firstWallId = idOf(withWalls.get("walls").get(0));

        OpeningRequest door = new OpeningRequest(
                null, firstWallId, OpeningType.DOOR, new BigDecimal("1000"), new BigDecimal("900"),
                new BigDecimal("2100"), null, null, null);
        JsonNode withDoor = readJson(putGeometry(levelId, token, new LevelGeometryRequest(
                        toNodeRequests(withWalls.get("nodes")), toWallRequests(withWalls.get("walls")),
                        List.of(door), List.of(), List.of()))
                .andExpect(status().isOk()));

        RoomGeometryRequest room = new RoomGeometryRequest(null, "Door Test Room", RoomType.LIVING_ROOM);
        JsonNode withRoomProbe = readJson(putGeometry(levelId, token, new LevelGeometryRequest(
                        toNodeRequests(withDoor.get("nodes")), toWallRequests(withDoor.get("walls")),
                        toOpeningRequests(withDoor.get("openings")), List.of(room), List.of()))
                .andExpect(status().isOk()));
        UUID roomId = idOf(withRoomProbe.get("rooms").get(0));

        List<RoomWallRequest> finalRoomWalls = new ArrayList<>();
        for (JsonNode w : withRoomProbe.get("walls")) {
            finalRoomWalls.add(new RoomWallRequest(roomId, idOf(w)));
        }
        putGeometry(levelId, token, new LevelGeometryRequest(
                        toNodeRequests(withRoomProbe.get("nodes")), toWallRequests(withRoomProbe.get("walls")),
                        toOpeningRequests(withRoomProbe.get("openings")),
                        List.of(new RoomGeometryRequest(roomId, "Door Test Room", RoomType.LIVING_ROOM)),
                        finalRoomWalls))
                .andExpect(status().isOk());

        UUID productId = createProduct("blocker", "600.00", "600.00").getId();
        // Same coordinates as the door-clearance unit test fixture: a door
        // at offset 1000/width 900 on a horizontal wall from (0,0)-(4000,0)
        // has its clearance zone centered around (1450, ~300-400).
        JsonNode result = savePlacements(roomId, token, List.of(new FurniturePlacementRequest(
                null, productId, new BigDecimal("1450"), new BigDecimal("300"), BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ONE, false)));

        assertThat(codesOf(result.get("issues"))).contains("DOOR_BLOCKED");
    }

    // ---- helpers ----

    private UUID createLevel() {
        User owner = createActiveUser("engine-project-owner");
        Project project = projectRepository.save(Project.builder()
                .owner(owner).name("Geometry Engine Test Project").projectType(ProjectType.RESIDENTIAL).build());
        Level level = levelRepository.save(Level.builder().project(project).name("Geometry Engine Test Level").build());
        return level.getId();
    }

    private Product createProduct(String name, String widthMm, String depthMm) {
        Vendor vendor = vendorRepository.save(Vendor.builder()
                .name("Engine Test Vendor " + UUID.randomUUID())
                .status(VendorStatus.ACTIVE)
                .build());
        return productRepository.save(Product.builder()
                .vendor(vendor)
                .name(name)
                .category("Test")
                .widthMm(new BigDecimal(widthMm))
                .depthMm(new BigDecimal(depthMm))
                .heightMm(new BigDecimal("800.00"))
                .status(ProductStatus.ACTIVE)
                .build());
    }

    private ResultActions putGeometry(UUID levelId, String token, LevelGeometryRequest request) throws Exception {
        return mockMvc.perform(put("/api/levels/{levelId}/geometry", levelId)
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));
    }

    private JsonNode savePlacements(UUID roomId, String token, List<FurniturePlacementRequest> requests) throws Exception {
        String body = mockMvc.perform(put("/api/rooms/{roomId}/placements", roomId)
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requests)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    private JsonNode saveSingleWall(UUID levelId, String token, int lengthMm) throws Exception {
        List<NodeRequest> nodeRequests = List.of(
                new NodeRequest(null, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO),
                new NodeRequest(null, new BigDecimal(lengthMm), BigDecimal.ZERO, BigDecimal.ZERO));
        JsonNode nodesOnly = readJson(putGeometry(levelId, token,
                new LevelGeometryRequest(nodeRequests, List.of(), List.of(), List.of(), List.of()))
                .andExpect(status().isOk()));

        WallRequest wall = new WallRequest(
                null, idOf(nodesOnly.get("nodes").get(0)), idOf(nodesOnly.get("nodes").get(1)),
                new BigDecimal("100"), new BigDecimal("2700"), WallKind.INTERIOR);
        return readJson(putGeometry(levelId, token, new LevelGeometryRequest(
                        toNodeRequests(nodesOnly.get("nodes")), List.of(wall), List.of(), List.of(), List.of()))
                .andExpect(status().isOk()));
    }

    /** Saves a 4000x3000mm rectangular room's walls (two-phase), no room/openings yet. */
    private JsonNode saveRectangleWalls(UUID levelId, String token) throws Exception {
        List<NodeRequest> nodeRequests = List.of(
                new NodeRequest(null, new BigDecimal("0"), new BigDecimal("0"), BigDecimal.ZERO),
                new NodeRequest(null, new BigDecimal("4000"), new BigDecimal("0"), BigDecimal.ZERO),
                new NodeRequest(null, new BigDecimal("4000"), new BigDecimal("3000"), BigDecimal.ZERO),
                new NodeRequest(null, new BigDecimal("0"), new BigDecimal("3000"), BigDecimal.ZERO));
        JsonNode nodesOnly = readJson(putGeometry(levelId, token,
                new LevelGeometryRequest(nodeRequests, List.of(), List.of(), List.of(), List.of()))
                .andExpect(status().isOk()));
        List<UUID> nodeIds = new ArrayList<>();
        for (JsonNode n : nodesOnly.get("nodes")) {
            nodeIds.add(idOf(n));
        }
        List<WallRequest> wallRequests = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            wallRequests.add(new WallRequest(
                    null, nodeIds.get(i), nodeIds.get((i + 1) % 4),
                    new BigDecimal("100"), new BigDecimal("2700"), WallKind.INTERIOR));
        }
        return readJson(putGeometry(levelId, token, new LevelGeometryRequest(
                        toNodeRequests(nodesOnly.get("nodes")), wallRequests, List.of(), List.of(), List.of()))
                .andExpect(status().isOk()));
    }

    /** Saves a 4000x3000mm rectangular room, with a Room entity bordered by all 4 walls (closed). */
    private JsonNode saveClosedRoom(UUID levelId, String token) throws Exception {
        JsonNode withWalls = saveRectangleWalls(levelId, token);

        RoomGeometryRequest room = new RoomGeometryRequest(null, "Engine Test Room", RoomType.LIVING_ROOM);
        JsonNode withRoomProbe = readJson(putGeometry(levelId, token, new LevelGeometryRequest(
                        toNodeRequests(withWalls.get("nodes")), toWallRequests(withWalls.get("walls")),
                        List.of(), List.of(room), List.of()))
                .andExpect(status().isOk()));
        UUID roomId = idOf(withRoomProbe.get("rooms").get(0));

        List<RoomWallRequest> roomWalls = new ArrayList<>();
        for (JsonNode w : withRoomProbe.get("walls")) {
            roomWalls.add(new RoomWallRequest(roomId, idOf(w)));
        }
        return readJson(putGeometry(levelId, token, new LevelGeometryRequest(
                        toNodeRequests(withRoomProbe.get("nodes")), toWallRequests(withRoomProbe.get("walls")),
                        List.of(), List.of(new RoomGeometryRequest(roomId, "Engine Test Room", RoomType.LIVING_ROOM)),
                        roomWalls))
                .andExpect(status().isOk()));
    }

    private JsonNode readJson(ResultActions actions) throws Exception {
        MvcResult result = actions.andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private UUID idOf(JsonNode node) {
        return UUID.fromString(node.get("id").asText());
    }

    private List<String> codesOf(JsonNode issues) {
        List<String> codes = new ArrayList<>();
        for (JsonNode issue : issues) {
            codes.add(issue.get("code").asText());
        }
        return codes;
    }

    private List<NodeRequest> toNodeRequests(JsonNode nodes) {
        List<NodeRequest> result = new ArrayList<>();
        for (JsonNode n : nodes) {
            result.add(new NodeRequest(
                    idOf(n), new BigDecimal(n.get("xMm").asText()),
                    new BigDecimal(n.get("yMm").asText()), new BigDecimal(n.get("zMm").asText())));
        }
        return result;
    }

    private List<WallRequest> toWallRequests(JsonNode walls) {
        List<WallRequest> result = new ArrayList<>();
        for (JsonNode w : walls) {
            result.add(new WallRequest(
                    UUID.fromString(w.get("id").asText()),
                    UUID.fromString(w.get("startNodeId").asText()),
                    UUID.fromString(w.get("endNodeId").asText()),
                    new BigDecimal(w.get("thicknessMm").asText()),
                    new BigDecimal(w.get("heightMm").asText()),
                    WallKind.INTERIOR));
        }
        return result;
    }

    private List<OpeningRequest> toOpeningRequests(JsonNode openings) {
        List<OpeningRequest> result = new ArrayList<>();
        for (JsonNode o : openings) {
            result.add(new OpeningRequest(
                    idOf(o), UUID.fromString(o.get("wallId").asText()),
                    OpeningType.valueOf(o.get("type").asText()),
                    new BigDecimal(o.get("offsetFromStartMm").asText()),
                    new BigDecimal(o.get("widthMm").asText()),
                    new BigDecimal(o.get("heightMm").asText()),
                    null, null, null));
        }
        return result;
    }
}



