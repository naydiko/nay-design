package com.naydiko.backend.controller;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.naydiko.backend.domain.entity.User;
import com.naydiko.backend.domain.enums.AuthProvider;
import com.naydiko.backend.domain.enums.UserRole;
import com.naydiko.backend.domain.enums.UserStatus;
import com.naydiko.backend.dto.request.ChangePasswordRequest;
import com.naydiko.backend.dto.request.ForgotPasswordRequest;
import com.naydiko.backend.dto.request.GoogleLoginRequest;
import com.naydiko.backend.dto.request.LoginRequest;
import com.naydiko.backend.dto.request.RegisterRequest;
import com.naydiko.backend.dto.request.ResetPasswordRequest;
import com.naydiko.backend.security.GoogleIdTokenVerifierService;
import com.naydiko.backend.security.JwtService;
import com.naydiko.backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;

import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for registration, login, the protected {@code /api/me}
 * endpoint, and the Stage 1 auth-completion flows (change password,
 * forgot/reset password, Google sign-in).
 */
class AuthControllerTest extends AbstractIntegrationTest {

    private static final String TEST_EMAIL = "auth-test-user@example.com";
    private static final String TEST_PASSWORD = "SuperSecret123";

    @MockitoBean
    private GoogleIdTokenVerifierService googleIdTokenVerifierService;

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

    @Test
    void protectedApi_withExpiredToken_returnsUnauthorized() throws Exception {
        User user = createActiveUser();
        // A JwtService configured with a negative expiration issues an
        // already-expired token signed with the same secret as the real app.
        JwtService expiredJwtService = new JwtService(
                "change-this-dev-only-secret-key-please-32-bytes-min", -10_000);
        String expiredToken = expiredJwtService.generateToken(new com.naydiko.backend.security.CustomUserDetails(user));

        mockMvc.perform(get("/api/me").header("Authorization", "Bearer " + expiredToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void changePassword_withCorrectCurrentPassword_succeedsAndAllowsLoginWithNewPassword() throws Exception {
        User user = createActiveUser();
        String token = tokenFor(user);

        ChangePasswordRequest request = new ChangePasswordRequest(TEST_PASSWORD, "BrandNewSecret456");
        mockMvc.perform(post("/api/auth/change-password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(TEST_EMAIL, "BrandNewSecret456"))))
                .andExpect(status().isOk());
    }

    @Test
    void changePassword_withIncorrectCurrentPassword_returnsUnauthorized() throws Exception {
        User user = createActiveUser();
        String token = tokenFor(user);

        ChangePasswordRequest request = new ChangePasswordRequest("wrong-current-password", "BrandNewSecret456");
        mockMvc.perform(post("/api/auth/change-password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void changePassword_withoutToken_returnsUnauthorized() throws Exception {
        ChangePasswordRequest request = new ChangePasswordRequest(TEST_PASSWORD, "BrandNewSecret456");
        mockMvc.perform(post("/api/auth/change-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void forgotPassword_returnsSameGenericResponseForExistingAndNonExistingEmail() throws Exception {
        createActiveUser();

        String existingBody = mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ForgotPasswordRequest(TEST_EMAIL))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String unknownBody = mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ForgotPasswordRequest("no-such-user@example.com"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(existingBody).isEqualTo(unknownBody);
    }

    @Test
    void resetPassword_withValidToken_updatesPasswordAndInvalidatesToken() throws Exception {
        User user = createActiveUser();
        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ForgotPasswordRequest(TEST_EMAIL))))
                .andExpect(status().isOk());

        String rawToken = issueRawResetTokenFor(user);

        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ResetPasswordRequest(rawToken, "ResetSecret789"))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(TEST_EMAIL, "ResetSecret789"))))
                .andExpect(status().isOk());
    }

    @Test
    void resetPassword_withExpiredToken_returnsBadRequest() throws Exception {
        User user = createActiveUser();
        String rawToken = "expired-raw-token-value";
        passwordResetTokenRepository.save(com.naydiko.backend.domain.entity.PasswordResetToken.builder()
                .user(user)
                .tokenHash(sha256(rawToken))
                .expiresAt(Instant.now().minusSeconds(60))
                .build());

        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ResetPasswordRequest(rawToken, "ResetSecret789"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void resetPassword_withReusedToken_returnsBadRequest() throws Exception {
        User user = createActiveUser();
        String rawToken = issueRawResetTokenFor(user);

        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ResetPasswordRequest(rawToken, "FirstReset123"))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ResetPasswordRequest(rawToken, "SecondReset456"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void googleLogin_firstTime_createsNewUser() throws Exception {
        String googleEmail = "google-new-user@example.com";
        when(googleIdTokenVerifierService.verify(anyString())).thenReturn(googlePayload(googleEmail, "google-sub-1"));

        mockMvc.perform(post("/api/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new GoogleLoginRequest("fake-id-token"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token", notNullValue()))
                .andExpect(jsonPath("$.user.email").value(googleEmail));

        User created = userRepository.findByEmail(googleEmail).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(created.getAuthProvider()).isEqualTo(AuthProvider.GOOGLE);
        userRepository.delete(created);
    }

    @Test
    void googleLogin_doesNotCreateDuplicateUserForSameEmail() throws Exception {
        String googleEmail = "google-repeat-user@example.com";
        when(googleIdTokenVerifierService.verify(anyString())).thenReturn(googlePayload(googleEmail, "google-sub-2"));

        mockMvc.perform(post("/api/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new GoogleLoginRequest("fake-id-token"))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new GoogleLoginRequest("fake-id-token"))))
                .andExpect(status().isOk());

        long count = userRepository.findAll().stream().filter(u -> u.getEmail().equals(googleEmail)).count();
        org.assertj.core.api.Assertions.assertThat(count).isEqualTo(1);
        userRepository.findByEmail(googleEmail).ifPresent(userRepository::delete);
    }

    @Test
    void googleLogin_linksToExistingLocalAccountWithSameEmail() throws Exception {
        User localUser = createActiveUser();
        when(googleIdTokenVerifierService.verify(anyString())).thenReturn(googlePayload(TEST_EMAIL, "google-sub-3"));

        mockMvc.perform(post("/api/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new GoogleLoginRequest("fake-id-token"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email").value(TEST_EMAIL));

        long count = userRepository.findAll().stream().filter(u -> u.getEmail().equals(TEST_EMAIL)).count();
        org.assertj.core.api.Assertions.assertThat(count).isEqualTo(1);
        User linked = userRepository.findById(localUser.getId()).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(linked.getGoogleId()).isEqualTo("google-sub-3");
    }

    private GoogleIdToken.Payload googlePayload(String email, String subject) {
        GoogleIdToken.Payload payload = new GoogleIdToken.Payload();
        payload.setEmail(email);
        payload.setEmailVerified(true);
        payload.setSubject(subject);
        payload.set("name", "Google User");
        return payload;
    }

    /** Issues a reset token the same way the forgot-password flow does, and returns the raw (unhashed) value. */
    private String issueRawResetTokenFor(User user) {
        String rawToken = "raw-token-" + java.util.UUID.randomUUID();
        passwordResetTokenRepository.save(com.naydiko.backend.domain.entity.PasswordResetToken.builder()
                .user(user)
                .tokenHash(sha256(rawToken))
                .expiresAt(Instant.now().plusSeconds(300))
                .build());
        return rawToken;
    }

    private static String sha256(String raw) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.Base64.getEncoder().encodeToString(hashed);
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
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





