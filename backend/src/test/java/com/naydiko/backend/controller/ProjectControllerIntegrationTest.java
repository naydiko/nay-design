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
 *
 * <p>Ownership is always derived from the authenticated caller (never a
 * client-supplied field), so every project created in these tests is owned
 * by whichever user's bearer token was used to create it.
 */
class ProjectControllerIntegrationTest extends AbstractIntegrationTest {

    @Test
    void create_returnsCreatedProjectOwnedByCaller() throws Exception {
        User owner = createActiveUser("project-owner");
        CreateProjectRequest request = new CreateProjectRequest(
                "Downtown Loft", "A bright loft renovation", ProjectType.RENOVATION,
                new BigDecimal("1000.00"), new BigDecimal("5000.00"), "USD");

        mockMvc.perform(post("/api/projects")
                        .header("Authorization", bearer(tokenFor(owner)))
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
    void create_blankName_returnsBadRequest() throws Exception {
        CreateProjectRequest request = new CreateProjectRequest(
                "  ", null, ProjectType.OTHER, null, null, null);

        mockMvc.perform(post("/api/projects")
                        .header("Authorization", bearer(createUserAndToken("project-caller")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void list_returnsOnlyProjectsOwnedByCaller() throws Exception {
        String token = bearer(createUserAndToken("project-owner"));
        String otherToken = bearer(createUserAndToken("other-owner"));

        createProject("Owner Project A", token);
        createProject("Owner Project B", token);
        createProject("Other Owner Project", otherToken);

        mockMvc.perform(get("/api/projects").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[*].name").value(hasItem("Owner Project A")))
                .andExpect(jsonPath("$[*].name").value(hasItem("Owner Project B")));
    }

    @Test
    void get_returnsProjectById() throws Exception {
        String token = bearer(createUserAndToken("project-caller"));
        String id = createProject("Fetchable Project", token);

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
    void get_projectOwnedByAnotherUser_returnsForbidden() throws Exception {
        String ownerToken = bearer(createUserAndToken("project-owner"));
        String id = createProject("Someone Else's Project", ownerToken);

        String otherToken = bearer(createUserAndToken("other-user"));
        mockMvc.perform(get("/api/projects/{id}", id).header("Authorization", otherToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void update_appliesAllFieldsIncludingStatus() throws Exception {
        String token = bearer(createUserAndToken("project-caller"));
        String id = createProject("Before Update", token);

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
    void update_projectOwnedByAnotherUser_returnsForbidden() throws Exception {
        String ownerToken = bearer(createUserAndToken("project-owner"));
        String id = createProject("Not Yours", ownerToken);

        UpdateProjectRequest update = new UpdateProjectRequest(
                "Hijacked", null, ProjectType.OTHER, ProjectStatus.DRAFT, null, null, null);

        String otherToken = bearer(createUserAndToken("other-user"));
        mockMvc.perform(put("/api/projects/{id}", id)
                        .header("Authorization", otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isForbidden());
    }

    @Test
    void delete_removesProjectSoItIsNoLongerFetchable() throws Exception {
        String token = bearer(createUserAndToken("project-caller"));
        String id = createProject("To Be Deleted", token);

        mockMvc.perform(delete("/api/projects/{id}", id).header("Authorization", token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/projects/{id}", id).header("Authorization", token))
                .andExpect(status().isNotFound());
    }

    @Test
    void delete_projectOwnedByAnotherUser_returnsForbidden() throws Exception {
        String ownerToken = bearer(createUserAndToken("project-owner"));
        String id = createProject("Protected", ownerToken);

        String otherToken = bearer(createUserAndToken("other-user"));
        mockMvc.perform(delete("/api/projects/{id}", id).header("Authorization", otherToken))
                .andExpect(status().isForbidden());
    }

    private String createProject(String name, String bearerToken) throws Exception {
        CreateProjectRequest request = new CreateProjectRequest(
                name, null, ProjectType.RESIDENTIAL, null, null, null);
        String body = mockMvc.perform(post("/api/projects")
                        .header("Authorization", bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asText();
    }
}




