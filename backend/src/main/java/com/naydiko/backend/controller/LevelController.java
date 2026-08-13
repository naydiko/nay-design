package com.naydiko.backend.controller;

import com.naydiko.backend.dto.request.CreateLevelRequest;
import com.naydiko.backend.dto.request.UpdateLevelRequest;
import com.naydiko.backend.dto.response.LevelResponse;
import com.naydiko.backend.service.LevelService;
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
 * REST API for managing project levels (floors/storeys).
 */
@Tag(name = "Levels")
@RestController
@RequestMapping("/api/levels")
public class LevelController {

    private final LevelService levelService;

    public LevelController(LevelService levelService) {
        this.levelService = levelService;
    }

    @Operation(summary = "Create a new level")
    @PostMapping
    public ResponseEntity<LevelResponse> createLevel(@Valid @RequestBody CreateLevelRequest request) {
        LevelResponse response = levelService.createLevel(request);
        return ResponseEntity.created(URI.create("/api/levels/" + response.id())).body(response);
    }

    @Operation(summary = "Get a level by id")
    @GetMapping("/{id}")
    public ResponseEntity<LevelResponse> getLevel(@Parameter(description = "Level id") @PathVariable UUID id) {
        return ResponseEntity.ok(levelService.getLevel(id));
    }

    @Operation(summary = "List levels belonging to a given project")
    @GetMapping
    public ResponseEntity<List<LevelResponse>> listLevels(
            @Parameter(description = "Project id", required = true) @RequestParam UUID projectId) {
        return ResponseEntity.ok(levelService.listLevelsByProject(projectId));
    }

    @Operation(summary = "Update an existing level")
    @PutMapping("/{id}")
    public ResponseEntity<LevelResponse> updateLevel(
            @Parameter(description = "Level id") @PathVariable UUID id,
            @Valid @RequestBody UpdateLevelRequest request) {
        return ResponseEntity.ok(levelService.updateLevel(id, request));
    }

    @Operation(summary = "Delete a level")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteLevel(@Parameter(description = "Level id") @PathVariable UUID id) {
        levelService.deleteLevel(id);
        return ResponseEntity.noContent().build();
    }
}

