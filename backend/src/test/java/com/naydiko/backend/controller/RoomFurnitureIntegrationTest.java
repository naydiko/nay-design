package com.naydiko.backend.controller;

import com.naydiko.backend.domain.entity.Level;
import com.naydiko.backend.domain.entity.Product;
import com.naydiko.backend.domain.entity.Project;
import com.naydiko.backend.domain.entity.Room;
import com.naydiko.backend.domain.entity.User;
import com.naydiko.backend.domain.entity.Vendor;
import com.naydiko.backend.domain.enums.ProductStatus;
import com.naydiko.backend.domain.enums.ProjectType;
import com.naydiko.backend.domain.enums.RoomType;
import com.naydiko.backend.domain.enums.VendorStatus;
import com.naydiko.backend.domain.repository.LevelRepository;
import com.naydiko.backend.domain.repository.ProductRepository;
import com.naydiko.backend.domain.repository.ProjectRepository;
import com.naydiko.backend.domain.repository.RoomRepository;
import com.naydiko.backend.domain.repository.VendorRepository;
import com.naydiko.backend.dto.request.FurniturePlacementRequest;
import com.naydiko.backend.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for a room's furniture layout API
 * ({@code /api/rooms/{roomId}/placements}).
 */
class RoomFurnitureIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private ProjectRepository projectRepository;
    @Autowired
    private LevelRepository levelRepository;
    @Autowired
    private RoomRepository roomRepository;
    @Autowired
    private VendorRepository vendorRepository;
    @Autowired
    private ProductRepository productRepository;

    @Test
    void save_createsNewPlacement() throws Exception {
        UUID roomId = createRoom();
        UUID productId = createProduct().getId();
        String token = bearer(createUserAndToken("furniture-caller"));

        FurniturePlacementRequest request = new FurniturePlacementRequest(
                null, productId, new BigDecimal("1000"), new BigDecimal("1200"), BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ONE, false);

        mockMvc.perform(put("/api/rooms/{roomId}/placements", roomId)
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(request))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.placements.length()").value(1))
                .andExpect(jsonPath("$.placements[0].id").exists())
                .andExpect(jsonPath("$.placements[0].productId").value(productId.toString()))
                .andExpect(jsonPath("$.placements[0].xMm").value(1000.0))
                .andExpect(jsonPath("$.issues").isArray());
    }

    @Test
    void load_returnsPreviouslySavedPlacements() throws Exception {
        UUID roomId = createRoom();
        UUID productId = createProduct().getId();
        String token = bearer(createUserAndToken("furniture-caller"));

        savePlacements(roomId, token, List.of(new FurniturePlacementRequest(
                null, productId, new BigDecimal("500"), new BigDecimal("600"), BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ONE, false)));

        mockMvc.perform(get("/api/rooms/{roomId}/placements", roomId).header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].productId").value(productId.toString()))
                .andExpect(jsonPath("$[0].xMm").value(500.0));
    }

    @Test
    void update_movesExistingPlacementWithoutDuplicating() throws Exception {
        UUID roomId = createRoom();
        UUID productId = createProduct().getId();
        String token = bearer(createUserAndToken("furniture-caller"));

        JsonNode saved = savePlacements(roomId, token, List.of(new FurniturePlacementRequest(
                null, productId, new BigDecimal("500"), new BigDecimal("600"), BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ONE, false)));
        UUID placementId = UUID.fromString(saved.get("placements").get(0).get("id").asText());

        FurniturePlacementRequest moved = new FurniturePlacementRequest(
                placementId, productId, new BigDecimal("1800"), new BigDecimal("2200"), BigDecimal.ZERO,
                new BigDecimal("90"), BigDecimal.ONE, false);

        mockMvc.perform(put("/api/rooms/{roomId}/placements", roomId)
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(moved))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.placements.length()").value(1))
                .andExpect(jsonPath("$.placements[0].id").value(placementId.toString()))
                .andExpect(jsonPath("$.placements[0].xMm").value(1800.0))
                .andExpect(jsonPath("$.placements[0].rotationAngle").value(90.0));
    }

    @Test
    void update_omittingAPlacement_removesIt() throws Exception {
        UUID roomId = createRoom();
        UUID productId = createProduct().getId();
        String token = bearer(createUserAndToken("furniture-caller"));

        JsonNode saved = savePlacements(roomId, token, List.of(
                new FurniturePlacementRequest(null, productId, new BigDecimal("100"), new BigDecimal("100"),
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ONE, false),
                new FurniturePlacementRequest(null, productId, new BigDecimal("2000"), new BigDecimal("2000"),
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ONE, false)));
        assertThat(saved.get("placements")).hasSize(2);
        UUID keepId = UUID.fromString(saved.get("placements").get(0).get("id").asText());

        FurniturePlacementRequest kept = new FurniturePlacementRequest(
                keepId, productId, new BigDecimal("100"), new BigDecimal("100"), BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ONE, false);

        mockMvc.perform(put("/api/rooms/{roomId}/placements", roomId)
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(kept))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.placements.length()").value(1))
                .andExpect(jsonPath("$.placements[0].id").value(keepId.toString()));

        mockMvc.perform(get("/api/rooms/{roomId}/placements", roomId).header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void update_withEmptyList_removesAllPlacements() throws Exception {
        UUID roomId = createRoom();
        UUID productId = createProduct().getId();
        String token = bearer(createUserAndToken("furniture-caller"));

        savePlacements(roomId, token, List.of(new FurniturePlacementRequest(
                null, productId, new BigDecimal("100"), new BigDecimal("100"), BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ONE, false)));

        mockMvc.perform(put("/api/rooms/{roomId}/placements", roomId)
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.placements.length()").value(0));

        mockMvc.perform(get("/api/rooms/{roomId}/placements", roomId).header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void save_unknownProduct_returnsNotFound() throws Exception {
        UUID roomId = createRoom();
        String token = bearer(createUserAndToken("furniture-caller"));

        FurniturePlacementRequest request = new FurniturePlacementRequest(
                null, UUID.randomUUID(), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ONE, false);

        mockMvc.perform(put("/api/rooms/{roomId}/placements", roomId)
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(request))))
                .andExpect(status().isNotFound());
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

    private UUID createRoom() {
        User owner = createActiveUser("furniture-project-owner");
        Project project = projectRepository.save(Project.builder()
                .owner(owner).name("Furniture Test Project").projectType(ProjectType.RESIDENTIAL).build());
        Level level = levelRepository.save(Level.builder().project(project).name("Furniture Test Level").build());
        Room room = roomRepository.save(Room.builder().level(level).name("Furniture Test Room").type(RoomType.LIVING_ROOM).build());
        return room.getId();
    }

    private Product createProduct() {
        Vendor vendor = vendorRepository.save(Vendor.builder()
                .name("Furniture Test Vendor " + UUID.randomUUID())
                .status(VendorStatus.ACTIVE)
                .build());
        return productRepository.save(Product.builder()
                .vendor(vendor)
                .name("Test Sofa")
                .category("Sofa")
                .widthMm(new BigDecimal("1800.00"))
                .depthMm(new BigDecimal("800.00"))
                .heightMm(new BigDecimal("850.00"))
                .status(ProductStatus.ACTIVE)
                .build());
    }
}

