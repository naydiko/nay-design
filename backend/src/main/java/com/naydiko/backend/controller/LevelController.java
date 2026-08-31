package com.naydiko.backend.controller;

import com.naydiko.backend.dto.request.CreateLevelRequest;
import com.naydiko.backend.dto.request.LevelGeometryRequest;
import com.naydiko.backend.dto.request.UpdateLevelRequest;
import com.naydiko.backend.dto.response.LevelGeometryResponse;
import com.naydiko.backend.dto.response.LevelResponse;
import com.naydiko.backend.service.LevelGeometryService;
import com.naydiko.backend.service.LevelService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * REST API for managing project levels (floors/storeys), including their
 * complete geometry document (nodes, walls, openings, rooms, room-wall borders).
 */
@Tag(name = "Levels")
@RestController
public class LevelController {

    private final LevelService levelService;
    private final LevelGeometryService levelGeometryService;

    public LevelController(LevelService levelService, LevelGeometryService levelGeometryService) {
        this.levelService = levelService;
        this.levelGeometryService = levelGeometryService;
    }

    @Operation(summary = "Create a new level within a project")
    @PostMapping("/api/projects/{projectId}/levels")
    public ResponseEntity<LevelResponse> createLevel(
            @Parameter(description = "Project id") @PathVariable UUID projectId,
            @Valid @RequestBody CreateLevelRequest request) {
        LevelResponse response = levelService.createLevel(projectId, request);
        return ResponseEntity.created(URI.create("/api/levels/" + response.id())).body(response);
    }

    @Operation(summary = "List levels belonging to a given project")
    @GetMapping("/api/projects/{projectId}/levels")
    public ResponseEntity<List<LevelResponse>> listLevels(
            @Parameter(description = "Project id") @PathVariable UUID projectId) {
        return ResponseEntity.ok(levelService.listLevelsByProject(projectId));
    }

    @Operation(summary = "Get a level by id")
    @GetMapping("/api/levels/{id}")
    public ResponseEntity<LevelResponse> getLevel(@Parameter(description = "Level id") @PathVariable UUID id) {
        return ResponseEntity.ok(levelService.getLevel(id));
    }

    @Operation(summary = "Update an existing level")
    @PatchMapping("/api/levels/{id}")
    public ResponseEntity<LevelResponse> updateLevel(
            @Parameter(description = "Level id") @PathVariable UUID id,
            @Valid @RequestBody UpdateLevelRequest request) {
        return ResponseEntity.ok(levelService.updateLevel(id, request));
    }

    @Operation(summary = "Delete a level")
    @DeleteMapping("/api/levels/{id}")
    public ResponseEntity<Void> deleteLevel(@Parameter(description = "Level id") @PathVariable UUID id) {
        levelService.deleteLevel(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Get the complete geometry (nodes, walls, openings, rooms, room-wall borders) of a level")
    @GetMapping("/api/levels/{levelId}/geometry")
    public ResponseEntity<LevelGeometryResponse> getGeometry(
            @Parameter(description = "Level id") @PathVariable UUID levelId) {
        return ResponseEntity.ok(levelGeometryService.getGeometry(levelId));
    }

    @Operation(summary = "Save (replace) the complete geometry of a level")
    @PutMapping("/api/levels/{levelId}/geometry")
    public ResponseEntity<LevelGeometryResponse> saveGeometry(
            @Parameter(description = "Level id") @PathVariable UUID levelId,
            @Valid @RequestBody LevelGeometryRequest request) {
        return ResponseEntity.ok(levelGeometryService.saveGeometry(levelId, request));
    }
}

