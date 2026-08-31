package com.naydiko.backend.controller;

import com.naydiko.backend.dto.request.UpdateUserRequest;
import com.naydiko.backend.dto.response.UserResponse;
import com.naydiko.backend.security.CustomUserDetails;
import com.naydiko.backend.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * REST API for user self-service profile management.
 *
 * <p>There is intentionally no "list all users" endpoint (no admin console
 * exists yet, and exposing every user would be a privacy leak) and no hard
 * delete (per {@link com.naydiko.backend.domain.entity.User}, users are
 * never hard-deleted while they may own projects). Registration and login
 * are handled by {@link AuthController}; the currently authenticated user's
 * profile is available via {@link MeController}.
 *
 * <p>Every operation here is restricted to the caller's own account: this
 * is self-service, not an admin API, and Stage 1 has no roles/permissions
 * model yet to safely allow otherwise.
 */
@Tag(name = "Users")
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @Operation(summary = "Get a user by id (self only)")
    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> getUser(
            @Parameter(description = "User id") @PathVariable UUID id,
            @AuthenticationPrincipal CustomUserDetails principal) {
        requireSelf(id, principal);
        return ResponseEntity.ok(userService.getUser(id));
    }

    @Operation(summary = "Update a user's profile (self only)")
    @PatchMapping("/{id}")
    public ResponseEntity<UserResponse> updateUser(
            @Parameter(description = "User id") @PathVariable UUID id,
            @Valid @RequestBody UpdateUserRequest request,
            @AuthenticationPrincipal CustomUserDetails principal) {
        requireSelf(id, principal);
        return ResponseEntity.ok(userService.updateUser(id, request));
    }

    private void requireSelf(UUID id, CustomUserDetails principal) {
        if (principal == null || !principal.getId().equals(id)) {
            throw new AccessDeniedException("You may only access your own user profile");
        }
    }
}

