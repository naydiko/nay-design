package com.naydiko.backend.controller;

import com.naydiko.backend.dto.request.CreateRoomRequest;
import com.naydiko.backend.dto.request.FurniturePlacementRequest;
import com.naydiko.backend.dto.request.UpdateRoomRequest;
import com.naydiko.backend.dto.response.FurniturePlacementResponse;
import com.naydiko.backend.dto.response.RoomResponse;
import com.naydiko.backend.service.RoomService;
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
 * REST API for managing rooms within a level, including their furniture layout.
 */
@Tag(name = "Rooms")
@RestController
public class RoomController {

    private final RoomService roomService;

    public RoomController(RoomService roomService) {
        this.roomService = roomService;
    }

    @Operation(summary = "Create a new room within a level")
    @PostMapping("/api/levels/{levelId}/rooms")
    public ResponseEntity<RoomResponse> createRoom(
            @Parameter(description = "Level id") @PathVariable UUID levelId,
            @Valid @RequestBody CreateRoomRequest request) {
        RoomResponse response = roomService.createRoom(levelId, request);
        return ResponseEntity.created(URI.create("/api/rooms/" + response.id())).body(response);
    }

    @Operation(summary = "List rooms belonging to a given level")
    @GetMapping("/api/levels/{levelId}/rooms")
    public ResponseEntity<List<RoomResponse>> listRooms(
            @Parameter(description = "Level id") @PathVariable UUID levelId) {
        return ResponseEntity.ok(roomService.listRoomsByLevel(levelId));
    }

    @Operation(summary = "Get a room by id")
    @GetMapping("/api/rooms/{id}")
    public ResponseEntity<RoomResponse> getRoom(@Parameter(description = "Room id") @PathVariable UUID id) {
        return ResponseEntity.ok(roomService.getRoom(id));
    }

    @Operation(summary = "Update an existing room")
    @PatchMapping("/api/rooms/{id}")
    public ResponseEntity<RoomResponse> updateRoom(
            @Parameter(description = "Room id") @PathVariable UUID id,
            @Valid @RequestBody UpdateRoomRequest request) {
        return ResponseEntity.ok(roomService.updateRoom(id, request));
    }

    @Operation(summary = "Delete a room")
    @DeleteMapping("/api/rooms/{id}")
    public ResponseEntity<Void> deleteRoom(@Parameter(description = "Room id") @PathVariable UUID id) {
        roomService.deleteRoom(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Get the current furniture layout of a room")
    @GetMapping("/api/rooms/{roomId}/placements")
    public ResponseEntity<List<FurniturePlacementResponse>> getPlacements(
            @Parameter(description = "Room id") @PathVariable UUID roomId) {
        return ResponseEntity.ok(roomService.getPlacements(roomId));
    }

    @Operation(summary = "Save (replace) the current furniture layout of a room")
    @PutMapping("/api/rooms/{roomId}/placements")
    public ResponseEntity<List<FurniturePlacementResponse>> savePlacements(
            @Parameter(description = "Room id") @PathVariable UUID roomId,
            @Valid @RequestBody List<FurniturePlacementRequest> placements) {
        RoomService.PlacementsSaveResult result = roomService.savePlacementsWithWarnings(roomId, placements);

        ResponseEntity.BodyBuilder response = ResponseEntity.ok();
        if (!result.warnings().isEmpty()) {
            // Non-blocking Geometry Engine findings (e.g. furniture outside
            // the room, overlapping walls/furniture, or blocking a door).
            // Kept out of the JSON body to preserve the existing List<...>
            // response contract; exposed via a header instead (see
            // SecurityConfig's CORS exposedHeaders).
            String encoded = result.warnings().stream()
                    .map(issue -> issue.code() + ": " + issue.message().replace("\n", " "))
                    .collect(java.util.stream.Collectors.joining(" | "));
            response.header("X-Geometry-Warnings", encoded);
        }
        return response.body(result.placements());
    }
}

