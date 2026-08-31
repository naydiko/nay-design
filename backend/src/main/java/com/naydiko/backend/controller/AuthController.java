package com.naydiko.backend.controller;

import com.naydiko.backend.dto.request.ChangePasswordRequest;
import com.naydiko.backend.dto.request.ForgotPasswordRequest;
import com.naydiko.backend.dto.request.GoogleLoginRequest;
import com.naydiko.backend.dto.request.LoginRequest;
import com.naydiko.backend.dto.request.RegisterRequest;
import com.naydiko.backend.dto.request.ResetPasswordRequest;
import com.naydiko.backend.dto.response.AuthResponse;
import com.naydiko.backend.dto.response.MessageResponse;
import com.naydiko.backend.security.CustomUserDetails;
import com.naydiko.backend.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API for registering and authenticating users. {@code register},
 * {@code login}, {@code forgot-password}, {@code reset-password},
 * {@code google} and {@code verify-email} are public; {@code change-password}
 * requires authentication (see {@link com.naydiko.backend.security.SecurityConfig}).
 */
@Tag(name = "Auth")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(summary = "Register a new user account")
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.ok(authService.register(request));
    }

    @Operation(summary = "Authenticate with email and password")
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @Operation(summary = "Authenticate or register via a verified Google identity")
    @PostMapping("/google")
    public ResponseEntity<AuthResponse> loginWithGoogle(@Valid @RequestBody GoogleLoginRequest request) {
        return ResponseEntity.ok(authService.loginWithGoogle(request));
    }

    @Operation(summary = "Change the authenticated user's password")
    @PostMapping("/change-password")
    public ResponseEntity<MessageResponse> changePassword(
            @AuthenticationPrincipal CustomUserDetails principal,
            @Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(principal.getId(), request);
        return ResponseEntity.ok(new MessageResponse("Password changed successfully"));
    }

    @Operation(summary = "Request a password reset email (response never reveals whether the account exists)")
    @PostMapping("/forgot-password")
    public ResponseEntity<MessageResponse> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request);
        return ResponseEntity.ok(new MessageResponse(
                "If an account exists for that email, a password reset link has been sent."));
    }

    @Operation(summary = "Complete a password reset using a valid token")
    @PostMapping("/reset-password")
    public ResponseEntity<MessageResponse> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.ok(new MessageResponse("Password has been reset. You can now log in."));
    }

    @Operation(summary = "Verify an email address using a verification token")
    @GetMapping("/verify-email")
    public ResponseEntity<MessageResponse> verifyEmail(@RequestParam String token) {
        authService.verifyEmail(token);
        return ResponseEntity.ok(new MessageResponse("Email verified successfully"));
    }
}


