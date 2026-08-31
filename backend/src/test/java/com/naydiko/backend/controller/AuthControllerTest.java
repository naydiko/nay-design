package com.naydiko.backend.controller;

import com.naydiko.backend.domain.entity.User;
import com.naydiko.backend.domain.enums.UserRole;
import com.naydiko.backend.domain.enums.UserStatus;
import com.naydiko.backend.dto.request.LoginRequest;
import com.naydiko.backend.dto.request.RegisterRequest;
import com.naydiko.backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for registration, login and the protected {@code /api/me} endpoint.
 */
class AuthControllerTest extends AbstractIntegrationTest {

    private static final String TEST_EMAIL = "auth-test-user@example.com";
    private static final String TEST_PASSWORD = "SuperSecret123";


    @AfterEach
    void cleanUp() {
        userRepository.findByEmail(TEST_EMAIL).ifPresent(userRepository::delete);
    }

    @Test
    void register_createsUserAndReturnsToken() throws Exception {
        RegisterRequest request = new RegisterRequest(
                TEST_EMAIL, "Test User", "Test", "User", "+10000000000", TEST_PASSWORD);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token", notNullValue()))
                .andExpect(jsonPath("$.user.email").value(TEST_EMAIL));
    }

    @Test
    void register_duplicateEmail_returnsConflict() throws Exception {
        RegisterRequest request = new RegisterRequest(
                TEST_EMAIL, "Test User", "Test", "User", null, TEST_PASSWORD);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    void login_validCredentials_returnsToken() throws Exception {
        createActiveUser();

        LoginRequest request = new LoginRequest(TEST_EMAIL, TEST_PASSWORD);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token", notNullValue()))
                .andExpect(jsonPath("$.user.email").value(TEST_EMAIL));
    }

    @Test
    void login_invalidCredentials_returnsUnauthorized() throws Exception {
        createActiveUser();

        LoginRequest request = new LoginRequest(TEST_EMAIL, "wrong-password");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void me_withoutToken_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedApi_withoutToken_returnsUnauthorized() throws Exception {
        // /api/me is a special case handled above; verify a completely
        // unrelated protected resource is also rejected without a token.
        mockMvc.perform(get("/api/vendors"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedApi_withInvalidToken_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/vendors").header("Authorization", "Bearer not-a-real-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void me_withValidToken_returnsAuthenticatedUser() throws Exception {
        User user = createActiveUser();
        String token = tokenFor(user);

        mockMvc.perform(get("/api/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(TEST_EMAIL));
    }

    private User createActiveUser() {
        User user = User.builder()
                .email(TEST_EMAIL)
                .displayName("Test User")
                .passwordHash(passwordEncoder.encode(TEST_PASSWORD))
                .role(UserRole.CLIENT)
                .status(UserStatus.ACTIVE)
                .build();
        return userRepository.save(user);
    }
}



