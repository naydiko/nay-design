package com.naydiko.backend.controller;

import com.naydiko.backend.dto.response.UserResponse;
import com.naydiko.backend.security.CustomUserDetails;
import com.naydiko.backend.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Returns the profile of the currently authenticated user.
 */
@Tag(name = "Auth")
@RestController
public class MeController {

    private final UserService userService;

    public MeController(UserService userService) {
        this.userService = userService;
    }

    @Operation(summary = "Get the currently authenticated user's profile")
    @GetMapping("/api/me")
    public ResponseEntity<UserResponse> getCurrentUser(@AuthenticationPrincipal CustomUserDetails principal) {
        return ResponseEntity.ok(userService.getUser(principal.getId()));
    }
}

