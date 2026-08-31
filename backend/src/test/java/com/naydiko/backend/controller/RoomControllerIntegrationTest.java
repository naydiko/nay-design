package com.naydiko.backend.controller;

import com.naydiko.backend.domain.entity.Level;
import com.naydiko.backend.domain.entity.Project;
import com.naydiko.backend.domain.entity.User;
import com.naydiko.backend.domain.enums.CeilingType;
import com.naydiko.backend.domain.enums.ProjectType;
import com.naydiko.backend.domain.enums.RoomType;
import com.naydiko.backend.domain.repository.LevelRepository;
import com.naydiko.backend.domain.repository.ProjectRepository;
import com.naydiko.backend.dto.request.CreateRoomRequest;
import com.naydiko.backend.dto.request.UpdateRoomRequest;
import com.naydiko.backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.util.UUID;

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

    @Test
    void create_returnsCreatedRoomWithLocationHeader() throws Exception {
        UUID levelId = createLevel();
        String token = bearer(createUserAndToken("room-caller"));
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
        UUID levelId = createLevel();
        String token = bearer(createUserAndToken("room-caller"));
        createRoom(levelId, "Bedroom", RoomType.BEDROOM, token);
        createRoom(levelId, "Kitchen", RoomType.KITCHEN, token);

        mockMvc.perform(get("/api/levels/{levelId}/rooms", levelId).header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void get_returnsRoomById() throws Exception {
        UUID levelId = createLevel();
        String token = bearer(createUserAndToken("room-caller"));
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
        UUID levelId = createLevel();
        String token = bearer(createUserAndToken("room-caller"));
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
        UUID levelId = createLevel();
        String token = bearer(createUserAndToken("room-caller"));
        String roomId = createRoom(levelId, "Doomed Room", RoomType.OTHER, token);

        mockMvc.perform(delete("/api/rooms/{id}", roomId).header("Authorization", token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/rooms/{id}", roomId).header("Authorization", token))
                .andExpect(status().isNotFound());
    }

    private UUID createLevel() {
        User owner = createActiveUser("room-project-owner");
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
}

