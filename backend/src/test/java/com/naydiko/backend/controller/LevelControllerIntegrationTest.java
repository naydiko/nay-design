package com.naydiko.backend.controller;

import com.naydiko.backend.domain.entity.Project;
import com.naydiko.backend.domain.entity.User;
import com.naydiko.backend.domain.enums.ProjectType;
import com.naydiko.backend.domain.repository.ProjectRepository;
import com.naydiko.backend.dto.request.CreateLevelRequest;
import com.naydiko.backend.dto.request.UpdateLevelRequest;
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
 * Integration tests for the Level CRUD API ({@code /api/projects/{projectId}/levels},
 * {@code /api/levels/{id}}).
 */
class LevelControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private ProjectRepository projectRepository;

    @Test
    void create_returnsCreatedLevelWithLocationHeader() throws Exception {
        UUID projectId = createProject();
        String token = bearer(createUserAndToken("level-caller"));
        CreateLevelRequest request = new CreateLevelRequest("Ground Floor", new BigDecimal("0.00"), 0);

        mockMvc.perform(post("/api/projects/{projectId}/levels", projectId)
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.projectId").value(projectId.toString()))
                .andExpect(jsonPath("$.name").value("Ground Floor"))
                .andExpect(jsonPath("$.visible").value(true));
    }

    @Test
    void create_unknownProject_returnsNotFound() throws Exception {
        String token = bearer(createUserAndToken("level-caller"));
        CreateLevelRequest request = new CreateLevelRequest("Ghost Floor", null, null);

        mockMvc.perform(post("/api/projects/{projectId}/levels", UUID.randomUUID())
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    void list_returnsLevelsOrderedByOrderIndex() throws Exception {
        UUID projectId = createProject();
        String token = bearer(createUserAndToken("level-caller"));
        createLevel(projectId, "Second Floor", 1, token);
        createLevel(projectId, "Ground Floor", 0, token);

        mockMvc.perform(get("/api/projects/{projectId}/levels", projectId).header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("Ground Floor"))
                .andExpect(jsonPath("$[1].name").value("Second Floor"));
    }

    @Test
    void get_returnsLevelById() throws Exception {
        UUID projectId = createProject();
        String token = bearer(createUserAndToken("level-caller"));
        String levelId = createLevel(projectId, "Ground Floor", 0, token);

        mockMvc.perform(get("/api/levels/{id}", levelId).header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(levelId))
                .andExpect(jsonPath("$.name").value("Ground Floor"));
    }

    @Test
    void get_unknownId_returnsNotFound() throws Exception {
        mockMvc.perform(get("/api/levels/{id}", UUID.randomUUID())
                        .header("Authorization", bearer(createUserAndToken("level-caller"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void update_appliesAllFields() throws Exception {
        UUID projectId = createProject();
        String token = bearer(createUserAndToken("level-caller"));
        String levelId = createLevel(projectId, "Original Name", 0, token);

        UpdateLevelRequest update = new UpdateLevelRequest("Renamed Floor", new BigDecimal("3000.00"), 5, false);

        mockMvc.perform(patch("/api/levels/{id}", levelId)
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Renamed Floor"))
                .andExpect(jsonPath("$.elevationMm").value(3000.0))
                .andExpect(jsonPath("$.orderIndex").value(5))
                .andExpect(jsonPath("$.visible").value(false));
    }

    @Test
    void delete_removesLevelSoItIsNoLongerFetchable() throws Exception {
        UUID projectId = createProject();
        String token = bearer(createUserAndToken("level-caller"));
        String levelId = createLevel(projectId, "Doomed Floor", 0, token);

        mockMvc.perform(delete("/api/levels/{id}", levelId).header("Authorization", token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/levels/{id}", levelId).header("Authorization", token))
                .andExpect(status().isNotFound());
    }

    private UUID createProject() {
        User owner = createActiveUser("level-project-owner");
        Project project = Project.builder()
                .owner(owner)
                .name("Level Test Project")
                .projectType(ProjectType.RESIDENTIAL)
                .build();
        return projectRepository.save(project).getId();
    }

    private String createLevel(UUID projectId, String name, int orderIndex, String bearerToken) throws Exception {
        CreateLevelRequest request = new CreateLevelRequest(name, null, orderIndex);
        String body = mockMvc.perform(post("/api/projects/{projectId}/levels", projectId)
                        .header("Authorization", bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asText();
    }
}

