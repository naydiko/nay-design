package com.naydiko.backend.controller;

import com.naydiko.backend.domain.entity.User;
import com.naydiko.backend.dto.request.UpdateUserRequest;
import com.naydiko.backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the self-only access control on {@code /api/users/{id}} (see
 * {@link UserController}): a user may read/update their own profile, but
 * not another user's.
 */
class UserControllerIntegrationTest extends AbstractIntegrationTest {

    @Test
    void getUser_self_returnsProfile() throws Exception {
        User self = createActiveUser("self-user");
        String token = bearer(tokenFor(self));

        mockMvc.perform(get("/api/users/{id}", self.getId()).header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(self.getId().toString()));
    }

    @Test
    void getUser_otherUser_returnsForbidden() throws Exception {
        User self = createActiveUser("self-user");
        User other = createActiveUser("other-user");
        String token = bearer(tokenFor(self));

        mockMvc.perform(get("/api/users/{id}", other.getId()).header("Authorization", token))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateUser_self_appliesChanges() throws Exception {
        User self = createActiveUser("self-user");
        String token = bearer(tokenFor(self));
        UpdateUserRequest update = new UpdateUserRequest("New Name", "New", "Name", "+1234567890");

        mockMvc.perform(patch("/api/users/{id}", self.getId())
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("New Name"));
    }

    @Test
    void updateUser_otherUser_returnsForbiddenAndDoesNotApplyChanges() throws Exception {
        User self = createActiveUser("self-user");
        User other = createActiveUser("other-user");
        String otherOriginalName = other.getDisplayName();
        String token = bearer(tokenFor(self));
        UpdateUserRequest update = new UpdateUserRequest("Hijacked Name", null, null, null);

        mockMvc.perform(patch("/api/users/{id}", other.getId())
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isForbidden());

        // Confirm via the victim's own token that nothing actually changed.
        mockMvc.perform(get("/api/users/{id}", other.getId()).header("Authorization", bearer(tokenFor(other))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value(otherOriginalName));
    }
}

