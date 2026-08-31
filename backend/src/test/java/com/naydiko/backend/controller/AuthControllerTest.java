package com.naydiko.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.naydiko.backend.domain.entity.User;
import com.naydiko.backend.domain.enums.UserRole;
import com.naydiko.backend.domain.enums.UserStatus;
import com.naydiko.backend.domain.repository.UserRepository;
import com.naydiko.backend.dto.request.LoginRequest;
import com.naydiko.backend.dto.request.RegisterRequest;
import com.naydiko.backend.security.CustomUserDetails;
import com.naydiko.backend.security.JwtService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for registration, login and the protected {@code /api/me} endpoint.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTest {

    private static final String TEST_EMAIL = "auth-test-user@example.com";
    private static final String TEST_PASSWORD = "SuperSecret123";

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

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
    void me_withValidToken_returnsAuthenticatedUser() throws Exception {
        User user = createActiveUser();
        String token = jwtService.generateToken(new CustomUserDetails(user));

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



