package com.naydiko.backend.controller;

import com.naydiko.backend.domain.entity.User;
import com.naydiko.backend.dto.request.CreateProjectRequest;
import com.naydiko.backend.dto.request.UpdateProjectRequest;
import com.naydiko.backend.domain.enums.ProjectStatus;
import com.naydiko.backend.domain.enums.ProjectType;
import com.naydiko.backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.util.UUID;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for the Project CRUD API ({@code /api/projects}).
 */
class ProjectControllerIntegrationTest extends AbstractIntegrationTest {

    @Test
    void create_returnsCreatedProjectWithLocationHeader() throws Exception {
        User owner = createActiveUser("project-owner");
        CreateProjectRequest request = new CreateProjectRequest(
                owner.getId(), "Downtown Loft", "A bright loft renovation", ProjectType.RENOVATION,
                new BigDecimal("1000.00"), new BigDecimal("5000.00"), "USD");

        mockMvc.perform(post("/api/projects")
                        .header("Authorization", bearer(createUserAndToken("project-caller")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.containsString("/api/projects/")))
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.ownerId").value(owner.getId().toString()))
                .andExpect(jsonPath("$.name").value("Downtown Loft"))
                .andExpect(jsonPath("$.projectType").value("RENOVATION"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.currency").value("USD"));
    }

    @Test
    void create_unknownOwner_returnsNotFound() throws Exception {
        CreateProjectRequest request = new CreateProjectRequest(
                UUID.randomUUID(), "Orphan Project", null, ProjectType.OTHER, null, null, null);

        mockMvc.perform(post("/api/projects")
                        .header("Authorization", bearer(createUserAndToken("project-caller")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    void create_blankName_returnsBadRequest() throws Exception {
        User owner = createActiveUser("project-owner");
        CreateProjectRequest request = new CreateProjectRequest(
                owner.getId(), "  ", null, ProjectType.OTHER, null, null, null);

        mockMvc.perform(post("/api/projects")
                        .header("Authorization", bearer(createUserAndToken("project-caller")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void list_returnsOnlyProjectsForRequestedOwner() throws Exception {
        User owner = createActiveUser("project-owner");
        User otherOwner = createActiveUser("other-owner");
        String token = bearer(createUserAndToken("project-caller"));

        createProject(owner.getId(), "Owner Project A", token);
        createProject(owner.getId(), "Owner Project B", token);
        createProject(otherOwner.getId(), "Other Owner Project", token);

        mockMvc.perform(get("/api/projects").queryParam("ownerId", owner.getId().toString())
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[*].name").value(hasItem("Owner Project A")))
                .andExpect(jsonPath("$[*].name").value(hasItem("Owner Project B")));
    }

    @Test
    void get_returnsProjectById() throws Exception {
        User owner = createActiveUser("project-owner");
        String token = bearer(createUserAndToken("project-caller"));
        String id = createProject(owner.getId(), "Fetchable Project", token);

        mockMvc.perform(get("/api/projects/{id}", id).header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.name").value("Fetchable Project"));
    }

    @Test
    void get_unknownId_returnsNotFound() throws Exception {
        mockMvc.perform(get("/api/projects/{id}", UUID.randomUUID())
                        .header("Authorization", bearer(createUserAndToken("project-caller"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void update_appliesAllFieldsIncludingStatus() throws Exception {
        User owner = createActiveUser("project-owner");
        String token = bearer(createUserAndToken("project-caller"));
        String id = createProject(owner.getId(), "Before Update", token);

        UpdateProjectRequest update = new UpdateProjectRequest(
                "After Update", "Updated description", ProjectType.COMMERCIAL, ProjectStatus.ACTIVE,
                new BigDecimal("2000.00"), new BigDecimal("9000.00"), "EUR");

        mockMvc.perform(put("/api/projects/{id}", id)
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("After Update"))
                .andExpect(jsonPath("$.description").value("Updated description"))
                .andExpect(jsonPath("$.projectType").value("COMMERCIAL"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.currency").value("EUR"));

        mockMvc.perform(get("/api/projects/{id}", id).header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("After Update"));
    }

    @Test
    void delete_removesProjectSoItIsNoLongerFetchable() throws Exception {
        User owner = createActiveUser("project-owner");
        String token = bearer(createUserAndToken("project-caller"));
        String id = createProject(owner.getId(), "To Be Deleted", token);

        mockMvc.perform(delete("/api/projects/{id}", id).header("Authorization", token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/projects/{id}", id).header("Authorization", token))
                .andExpect(status().isNotFound());
    }

    private String createProject(UUID ownerId, String name, String bearerToken) throws Exception {
        CreateProjectRequest request = new CreateProjectRequest(
                ownerId, name, null, ProjectType.RESIDENTIAL, null, null, null);
        String body = mockMvc.perform(post("/api/projects")
                        .header("Authorization", bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asText();
    }
}


