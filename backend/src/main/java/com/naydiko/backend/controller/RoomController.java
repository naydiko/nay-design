package com.naydiko.backend.controller;

import com.naydiko.backend.dto.request.CreateRoomRequest;
import com.naydiko.backend.dto.request.FurniturePlacementRequest;
import com.naydiko.backend.dto.request.UpdateRoomRequest;
import com.naydiko.backend.dto.response.FurniturePlacementResponse;
import com.naydiko.backend.dto.response.GeometryIssueResponse;
import com.naydiko.backend.dto.response.RoomPlacementsSaveResponse;
import com.naydiko.backend.dto.response.RoomResponse;
import com.naydiko.backend.security.CustomUserDetails;
import com.naydiko.backend.service.RoomService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
 * REST API for managing rooms within a level, including their furniture layout.
 * Every operation is scoped to the authenticated caller: a room/level id
 * alone is never sufficient — the caller must own the parent project.
 */
@Tag(name = "Rooms")
@RestController
public class RoomController {

    private final RoomService roomService;

    public RoomController(RoomService roomService) {
        this.roomService = roomService;
    }

    @Operation(summary = "Create a new room within a level (parent project must be owned by the authenticated user)")
    @PostMapping("/api/levels/{levelId}/rooms")
    public ResponseEntity<RoomResponse> createRoom(
            @AuthenticationPrincipal CustomUserDetails principal,
            @Parameter(description = "Level id") @PathVariable UUID levelId,
            @Valid @RequestBody CreateRoomRequest request) {
        RoomResponse response = roomService.createRoom(levelId, principal.getId(), request);
        return ResponseEntity.created(URI.create("/api/rooms/" + response.id())).body(response);
    }

    @Operation(summary = "List rooms belonging to a given level (parent project must be owned by the authenticated user)")
    @GetMapping("/api/levels/{levelId}/rooms")
    public ResponseEntity<List<RoomResponse>> listRooms(
            @AuthenticationPrincipal CustomUserDetails principal,
            @Parameter(description = "Level id") @PathVariable UUID levelId) {
        return ResponseEntity.ok(roomService.listRoomsByLevel(levelId, principal.getId()));
    }

    @Operation(summary = "Get a room by id (parent project must be owned by the authenticated user)")
    @GetMapping("/api/rooms/{id}")
    public ResponseEntity<RoomResponse> getRoom(
            @AuthenticationPrincipal CustomUserDetails principal,
            @Parameter(description = "Room id") @PathVariable UUID id) {
        return ResponseEntity.ok(roomService.getRoom(id, principal.getId()));
    }

    @Operation(summary = "Update an existing room (parent project must be owned by the authenticated user)")
    @PatchMapping("/api/rooms/{id}")
    public ResponseEntity<RoomResponse> updateRoom(
            @AuthenticationPrincipal CustomUserDetails principal,
            @Parameter(description = "Room id") @PathVariable UUID id,
            @Valid @RequestBody UpdateRoomRequest request) {
        return ResponseEntity.ok(roomService.updateRoom(id, principal.getId(), request));
    }

    @Operation(summary = "Delete a room (parent project must be owned by the authenticated user)")
    @DeleteMapping("/api/rooms/{id}")
    public ResponseEntity<Void> deleteRoom(
            @AuthenticationPrincipal CustomUserDetails principal,
            @Parameter(description = "Room id") @PathVariable UUID id) {
        roomService.deleteRoom(id, principal.getId());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Get the current furniture layout of a room (parent project must be owned by the authenticated user)")
    @GetMapping("/api/rooms/{roomId}/placements")
    public ResponseEntity<List<FurniturePlacementResponse>> getPlacements(
            @AuthenticationPrincipal CustomUserDetails principal,
            @Parameter(description = "Room id") @PathVariable UUID roomId) {
        return ResponseEntity.ok(roomService.getPlacements(roomId, principal.getId()));
    }

    @Operation(summary = "Save (replace) the current furniture layout of a room (parent project must be owned by the authenticated user)")
    @PutMapping("/api/rooms/{roomId}/placements")
    public ResponseEntity<RoomPlacementsSaveResponse> savePlacements(
            @AuthenticationPrincipal CustomUserDetails principal,
            @Parameter(description = "Room id") @PathVariable UUID roomId,
            @Valid @RequestBody List<FurniturePlacementRequest> placements) {
        RoomService.PlacementsSaveResult result = roomService.savePlacementsWithWarnings(roomId, principal.getId(), placements);

        // Non-blocking Geometry Engine findings (e.g. furniture outside the
        // room, overlapping walls/furniture, or blocking a door) surfaced in
        // the JSON body with full severity/code/related-entity detail, so
        // the frontend can render human-readable messages and highlight the
        // offending furniture. Stage 1 never rejects a save for these.
        List<GeometryIssueResponse> issues = result.warnings().stream()
                .map(GeometryIssueResponse::from)
                .toList();
        return ResponseEntity.ok(new RoomPlacementsSaveResponse(result.placements(), issues));
    }
}

