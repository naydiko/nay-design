package com.naydiko.backend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.naydiko.backend.domain.entity.Level;
import com.naydiko.backend.domain.entity.Project;
import com.naydiko.backend.domain.entity.User;
import com.naydiko.backend.domain.enums.OpeningType;
import com.naydiko.backend.domain.enums.ProjectType;
import com.naydiko.backend.domain.enums.WallKind;
import com.naydiko.backend.domain.repository.LevelRepository;
import com.naydiko.backend.domain.repository.ProjectRepository;
import com.naydiko.backend.dto.request.LevelGeometryRequest;
import com.naydiko.backend.dto.request.NodeRequest;
import com.naydiko.backend.dto.request.OpeningRequest;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for the level geometry document API
 * ({@code /api/levels/{levelId}/geometry}): saving, loading, updating,
 * rejecting invalid references, and rolling back the whole save when the
 * Geometry Engine finds a structural (error-severity) issue.
 *
 * <p>Because new walls/openings can only reference nodes/walls that already
 * have a server-assigned id (see {@code LevelGeometryService}), building a
 * brand-new rectangular room from scratch takes the same multi-phase save
 * the frontend canvas performs: persist nodes first, then walls, then
 * openings — each phase referencing only already-known real ids.
 */
class LevelGeometryIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private ProjectRepository projectRepository;
    @Autowired
    private LevelRepository levelRepository;

    @Test
    void save_persistsNodesWallsAndOpeningsWithNoIssues() throws Exception {
        UUID levelId = createLevel();
        String token = bearer(createUserAndToken("geometry-caller"));

        JsonNode result = saveRectangleWithDoor(levelId, token);

        assertThat(result.get("nodes")).hasSize(4);
        assertThat(result.get("walls")).hasSize(4);
        assertThat(result.get("openings")).hasSize(1);
        assertThat(result.get("issues")).isEmpty();
    }

    @Test
    void load_returnsPreviouslySavedGeometry() throws Exception {
        UUID levelId = createLevel();
        String token = bearer(createUserAndToken("geometry-caller"));
        saveRectangleWithDoor(levelId, token);

        mockMvc.perform(get("/api/levels/{levelId}/geometry", levelId).header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nodes.length()").value(4))
                .andExpect(jsonPath("$.walls.length()").value(4))
                .andExpect(jsonPath("$.openings.length()").value(1));
    }

    @Test
    void update_existingGeometryChangesWallWithoutDuplicatingNodesOrWalls() throws Exception {
        UUID levelId = createLevel();
        String token = bearer(createUserAndToken("geometry-caller"));
        JsonNode saved = saveRectangleWithDoor(levelId, token);

        List<NodeRequest> nodes = toNodeRequests(saved.get("nodes"));
        List<WallRequest> walls = toWallRequests(saved.get("walls"), 0, new BigDecimal("220.00"));
        List<OpeningRequest> openings = toOpeningRequests(saved.get("openings"));

        String updatedBody = putGeometry(levelId, token,
                        new LevelGeometryRequest(nodes, walls, openings, List.of(), List.of()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nodes.length()").value(4))
                .andExpect(jsonPath("$.walls.length()").value(4))
                .andExpect(jsonPath("$.openings.length()").value(1))
                .andReturn().getResponse().getContentAsString();

        JsonNode updated = objectMapper.readTree(updatedBody);
        UUID firstWallId = UUID.fromString(saved.get("walls").get(0).get("id").asText());
        assertThat(thicknessOf(updated, firstWallId)).isEqualByComparingTo(new BigDecimal("220.00"));
    }

    @Test
    void save_wallReferencingUnknownNode_returnsBadRequest() throws Exception {
        UUID levelId = createLevel();
        String token = bearer(createUserAndToken("geometry-caller"));

        NodeRequest n1 = new NodeRequest(null, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        WallRequest badWall = new WallRequest(
                null, UUID.randomUUID(), UUID.randomUUID(),
                new BigDecimal("100"), new BigDecimal("2700"), WallKind.INTERIOR);
        LevelGeometryRequest request = new LevelGeometryRequest(
                List.of(n1), List.of(badWall), List.of(), List.of(), List.of());

        putGeometry(levelId, token, request).andExpect(status().isBadRequest());
    }

    @Test
    void save_zeroLengthWall_returnsBadRequestWithGeometryIssue() throws Exception {
        UUID levelId = createLevel();
        String token = bearer(createUserAndToken("geometry-caller"));

        // Two distinct nodes at the *same* coordinates -> zero-length wall.
        // Persist the (coincident) nodes first so the wall below can
        // reference their real, server-assigned ids.
        NodeRequest n1 = new NodeRequest(null, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        NodeRequest n2 = new NodeRequest(null, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        String body = putGeometry(levelId, token,
                        new LevelGeometryRequest(List.of(n1, n2), List.of(), List.of(), List.of(), List.of()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode saved = objectMapper.readTree(body);
        UUID nodeAId = UUID.fromString(saved.get("nodes").get(0).get("id").asText());
        UUID nodeBId = UUID.fromString(saved.get("nodes").get(1).get("id").asText());

        WallRequest zeroLengthWall = new WallRequest(
                null, nodeAId, nodeBId, new BigDecimal("100"), new BigDecimal("2700"), WallKind.INTERIOR);
        LevelGeometryRequest withBadWall = new LevelGeometryRequest(
                toNodeRequests(saved.get("nodes")), List.of(zeroLengthWall), List.of(), List.of(), List.of());

        putGeometry(levelId, token, withBadWall)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[*].field").value(hasItem("WALL_ZERO_LENGTH")))
                .andExpect(jsonPath("$.issues[*].code").value(hasItem("WALL_ZERO_LENGTH")))
                .andExpect(jsonPath("$.issues[*].severity").value(hasItem("ERROR")));
    }

    @Test
    void update_withInvalidWall_rollsBackEntireSave() throws Exception {
        UUID levelId = createLevel();
        String token = bearer(createUserAndToken("geometry-caller"));

        JsonNode baseline = saveRectangle(levelId, token);
        UUID firstWallId = UUID.fromString(baseline.get("walls").get(0).get("id").asText());
        BigDecimal originalThickness = thicknessOf(baseline, firstWallId);

        // Pre-save two extra, coincident nodes in their own (successful)
        // request so the next request can reference their real ids for a
        // brand-new zero-length wall.
        List<NodeRequest> nodesPlusExtras = new ArrayList<>(toNodeRequests(baseline.get("nodes")));
        nodesPlusExtras.add(new NodeRequest(null, new BigDecimal("9000"), new BigDecimal("9000"), BigDecimal.ZERO));
        nodesPlusExtras.add(new NodeRequest(null, new BigDecimal("9000"), new BigDecimal("9000"), BigDecimal.ZERO));
        String withExtraNodesBody = putGeometry(levelId, token, new LevelGeometryRequest(
                        nodesPlusExtras, toWallRequests(baseline.get("walls"), -1, null), List.of(), List.of(), List.of()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode withExtraNodes = objectMapper.readTree(withExtraNodesBody);
        assertThat(withExtraNodes.get("nodes")).hasSize(6);

        List<UUID> extraNodeIds = new ArrayList<>();
        for (JsonNode n : withExtraNodes.get("nodes")) {
            if (n.get("xMm").asDouble() == 9000.0 && n.get("yMm").asDouble() == 9000.0) {
                extraNodeIds.add(UUID.fromString(n.get("id").asText()));
            }
        }
        assertThat(extraNodeIds).hasSize(2);

        // Now attempt an update that both (a) legitimately changes wall[0]'s
        // thickness and (b) adds a new zero-length wall between the two
        // coincident nodes above. The whole save must be rejected AND
        // rolled back, including part (a).
        List<WallRequest> wallsWithBadOne = new ArrayList<>(
                toWallRequests(withExtraNodes.get("walls"), 0, new BigDecimal("999.00")));
        wallsWithBadOne.add(new WallRequest(
                null, extraNodeIds.get(0), extraNodeIds.get(1),
                new BigDecimal("100"), new BigDecimal("2700"), WallKind.INTERIOR));

        putGeometry(levelId, token, new LevelGeometryRequest(
                        toNodeRequests(withExtraNodes.get("nodes")), wallsWithBadOne, List.of(), List.of(), List.of()))
                .andExpect(status().isBadRequest());

        // The whole save must have rolled back: wall[0]'s thickness is
        // unchanged, and the new (invalid) wall was not persisted.
        String afterBody = mockMvc.perform(get("/api/levels/{levelId}/geometry", levelId).header("Authorization", token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode after = objectMapper.readTree(afterBody);

        assertThat(after.get("walls")).hasSize(4); // not 5 - the bad wall never persisted
        assertThat(after.get("nodes")).hasSize(6); // the extra nodes from the earlier *successful* call remain
        assertThat(thicknessOf(after, firstWallId)).isEqualByComparingTo(originalThickness);
    }

    // ---- helpers ----

    private UUID createLevel() {
        User owner = createActiveUser("geometry-project-owner");
        Project project = projectRepository.save(Project.builder()
                .owner(owner)
                .name("Geometry Test Project")
                .projectType(ProjectType.RESIDENTIAL)
                .build());
        Level level = levelRepository.save(Level.builder()
                .project(project)
                .name("Geometry Test Level")
                .build());
        return level.getId();
    }

    private ResultActions putGeometry(UUID levelId, String token, LevelGeometryRequest request) throws Exception {
        return mockMvc.perform(put("/api/levels/{levelId}/geometry", levelId)
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));
    }

    /** Saves a plain 4000x3000mm rectangular room boundary (no openings), two-phase. */
    private JsonNode saveRectangle(UUID levelId, String token) throws Exception {
        List<NodeRequest> nodeRequests = List.of(
                new NodeRequest(null, new BigDecimal("0"), new BigDecimal("0"), BigDecimal.ZERO),
                new NodeRequest(null, new BigDecimal("4000"), new BigDecimal("0"), BigDecimal.ZERO),
                new NodeRequest(null, new BigDecimal("4000"), new BigDecimal("3000"), BigDecimal.ZERO),
                new NodeRequest(null, new BigDecimal("0"), new BigDecimal("3000"), BigDecimal.ZERO));
        MvcResult phase1 = putGeometry(levelId, token,
                        new LevelGeometryRequest(nodeRequests, List.of(), List.of(), List.of(), List.of()))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode nodesOnly = objectMapper.readTree(phase1.getResponse().getContentAsString());
        List<UUID> nodeIds = new ArrayList<>();
        for (JsonNode n : nodesOnly.get("nodes")) {
            nodeIds.add(UUID.fromString(n.get("id").asText()));
        }

        List<WallRequest> wallRequests = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            UUID start = nodeIds.get(i);
            UUID end = nodeIds.get((i + 1) % 4);
            wallRequests.add(new WallRequest(
                    null, start, end, new BigDecimal("100"), new BigDecimal("2700"), WallKind.INTERIOR));
        }
        MvcResult phase2 = putGeometry(levelId, token,
                        new LevelGeometryRequest(toNodeRequests(nodesOnly.get("nodes")), wallRequests, List.of(), List.of(), List.of()))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(phase2.getResponse().getContentAsString());
    }

    /** Saves a 4000x3000mm rectangular room with a single door on the first wall, three-phase. */
    private JsonNode saveRectangleWithDoor(UUID levelId, String token) throws Exception {
        JsonNode withWalls = saveRectangle(levelId, token);
        UUID firstWallId = UUID.fromString(withWalls.get("walls").get(0).get("id").asText());

        OpeningRequest door = new OpeningRequest(
                null, firstWallId, OpeningType.DOOR,
                new BigDecimal("1500"), new BigDecimal("900"), new BigDecimal("2100"),
                null, null, null);
        MvcResult phase3 = putGeometry(levelId, token, new LevelGeometryRequest(
                        toNodeRequests(withWalls.get("nodes")),
                        toWallRequests(withWalls.get("walls"), -1, null),
                        List.of(door), List.of(), List.of()))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(phase3.getResponse().getContentAsString());
    }

    private List<NodeRequest> toNodeRequests(JsonNode nodes) {
        List<NodeRequest> result = new ArrayList<>();
        for (JsonNode n : nodes) {
            result.add(new NodeRequest(
                    UUID.fromString(n.get("id").asText()),
                    new BigDecimal(n.get("xMm").asText()),
                    new BigDecimal(n.get("yMm").asText()),
                    new BigDecimal(n.get("zMm").asText())));
        }
        return result;
    }

    /** Converts response wall JSON back into request DTOs, optionally overriding one wall's thickness by index. */
    private List<WallRequest> toWallRequests(JsonNode walls, int overrideIndex, BigDecimal overrideThickness) {
        List<WallRequest> result = new ArrayList<>();
        int i = 0;
        for (JsonNode w : walls) {
            BigDecimal thickness = (i == overrideIndex)
                    ? overrideThickness
                    : new BigDecimal(w.get("thicknessMm").asText());
            result.add(new WallRequest(
                    UUID.fromString(w.get("id").asText()),
                    UUID.fromString(w.get("startNodeId").asText()),
                    UUID.fromString(w.get("endNodeId").asText()),
                    thickness,
                    new BigDecimal(w.get("heightMm").asText()),
                    WallKind.INTERIOR));
            i++;
        }
        return result;
    }

    private List<OpeningRequest> toOpeningRequests(JsonNode openings) {
        List<OpeningRequest> result = new ArrayList<>();
        for (JsonNode o : openings) {
            result.add(new OpeningRequest(
                    UUID.fromString(o.get("id").asText()),
                    UUID.fromString(o.get("wallId").asText()),
                    OpeningType.valueOf(o.get("type").asText()),
                    new BigDecimal(o.get("offsetFromStartMm").asText()),
                    new BigDecimal(o.get("widthMm").asText()),
                    new BigDecimal(o.get("heightMm").asText()),
                    null, null, null));
        }
        return result;
    }

    private BigDecimal thicknessOf(JsonNode geometryResponse, UUID wallId) {
        for (JsonNode w : geometryResponse.get("walls")) {
            if (w.get("id").asText().equals(wallId.toString())) {
                return new BigDecimal(w.get("thicknessMm").asText());
            }
        }
        throw new AssertionError("Wall not found in response: " + wallId);
    }
}

