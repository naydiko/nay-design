package com.naydiko.backend.controller;

import com.naydiko.backend.dto.request.CreateProjectRequest;
import com.naydiko.backend.dto.request.UpdateProjectRequest;
import com.naydiko.backend.dto.response.ProjectResponse;
import com.naydiko.backend.service.ProjectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * REST API for managing design projects.
 */
@Tag(name = "Projects")
@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @Operation(summary = "Create a new project")
    @PostMapping
    public ResponseEntity<ProjectResponse> createProject(@Valid @RequestBody CreateProjectRequest request) {
        ProjectResponse response = projectService.createProject(request);
        return ResponseEntity.created(URI.create("/api/projects/" + response.id())).body(response);
    }

    @Operation(summary = "Get a project by id")
    @GetMapping("/{id}")
    public ResponseEntity<ProjectResponse> getProject(@Parameter(description = "Project id") @PathVariable UUID id) {
        return ResponseEntity.ok(projectService.getProject(id));
    }

    @Operation(summary = "List projects owned by a given user")
    @GetMapping
    public ResponseEntity<List<ProjectResponse>> listProjects(
            @Parameter(description = "Owner id", required = true) @RequestParam UUID ownerId) {
        return ResponseEntity.ok(projectService.listProjectsByOwner(ownerId));
    }

    @Operation(summary = "Update an existing project")
    @PutMapping("/{id}")
    public ResponseEntity<ProjectResponse> updateProject(
            @Parameter(description = "Project id") @PathVariable UUID id,
            @Valid @RequestBody UpdateProjectRequest request) {
        return ResponseEntity.ok(projectService.updateProject(id, request));
    }

    @Operation(summary = "Delete a project")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProject(@Parameter(description = "Project id") @PathVariable UUID id) {
        projectService.deleteProject(id);
        return ResponseEntity.noContent().build();
    }
}

