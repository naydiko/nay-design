package com.naydiko.backend.controller;

import com.naydiko.backend.dto.request.CreateProjectRequest;
import com.naydiko.backend.dto.request.UpdateProjectRequest;
import com.naydiko.backend.dto.response.ProjectResponse;
import com.naydiko.backend.security.CustomUserDetails;
import com.naydiko.backend.service.ProjectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * REST API for managing design projects. All operations are scoped to the
 * authenticated caller: a user can only see, update, or delete their own
 * projects — ownership is never taken from client-supplied input.
 */
@Tag(name = "Projects")
@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @Operation(summary = "Create a new project owned by the authenticated user")
    @PostMapping
    public ResponseEntity<ProjectResponse> createProject(
            @AuthenticationPrincipal CustomUserDetails principal,
            @Valid @RequestBody CreateProjectRequest request) {
        ProjectResponse response = projectService.createProject(principal.getId(), request);
        return ResponseEntity.created(URI.create("/api/projects/" + response.id())).body(response);
    }

    @Operation(summary = "Get a project by id (must be owned by the authenticated user)")
    @GetMapping("/{id}")
    public ResponseEntity<ProjectResponse> getProject(
            @AuthenticationPrincipal CustomUserDetails principal,
            @Parameter(description = "Project id") @PathVariable UUID id) {
        return ResponseEntity.ok(projectService.getProjectForOwner(id, principal.getId()));
    }

    @Operation(summary = "List projects owned by the authenticated user")
    @GetMapping
    public ResponseEntity<List<ProjectResponse>> listProjects(@AuthenticationPrincipal CustomUserDetails principal) {
        return ResponseEntity.ok(projectService.listProjectsByOwner(principal.getId()));
    }

    @Operation(summary = "Update an existing project (must be owned by the authenticated user)")
    @PutMapping("/{id}")
    public ResponseEntity<ProjectResponse> updateProject(
            @AuthenticationPrincipal CustomUserDetails principal,
            @Parameter(description = "Project id") @PathVariable UUID id,
            @Valid @RequestBody UpdateProjectRequest request) {
        return ResponseEntity.ok(projectService.updateProject(id, principal.getId(), request));
    }

    @Operation(summary = "Delete a project (must be owned by the authenticated user)")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProject(
            @AuthenticationPrincipal CustomUserDetails principal,
            @Parameter(description = "Project id") @PathVariable UUID id) {
        projectService.deleteProject(id, principal.getId());
        return ResponseEntity.noContent().build();
    }
}



