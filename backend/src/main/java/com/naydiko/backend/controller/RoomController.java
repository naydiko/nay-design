package com.naydiko.backend.controller;

import com.naydiko.backend.dto.request.CreateRoomRequest;
import com.naydiko.backend.dto.request.UpdateRoomRequest;
import com.naydiko.backend.dto.response.RoomResponse;
import com.naydiko.backend.service.RoomService;
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
 * REST API for managing rooms within a level.
 */
@Tag(name = "Rooms")
@RestController
@RequestMapping("/api/rooms")
public class RoomController {

    private final RoomService roomService;

    public RoomController(RoomService roomService) {
        this.roomService = roomService;
    }

    @Operation(summary = "Create a new room")
    @PostMapping
    public ResponseEntity<RoomResponse> createRoom(@Valid @RequestBody CreateRoomRequest request) {
        RoomResponse response = roomService.createRoom(request);
        return ResponseEntity.created(URI.create("/api/rooms/" + response.id())).body(response);
    }

    @Operation(summary = "Get a room by id")
    @GetMapping("/{id}")
    public ResponseEntity<RoomResponse> getRoom(@Parameter(description = "Room id") @PathVariable UUID id) {
        return ResponseEntity.ok(roomService.getRoom(id));
    }

    @Operation(summary = "List rooms belonging to a given level")
    @GetMapping
    public ResponseEntity<List<RoomResponse>> listRooms(
            @Parameter(description = "Level id", required = true) @RequestParam UUID levelId) {
        return ResponseEntity.ok(roomService.listRoomsByLevel(levelId));
    }

    @Operation(summary = "Update an existing room")
    @PutMapping("/{id}")
    public ResponseEntity<RoomResponse> updateRoom(
            @Parameter(description = "Room id") @PathVariable UUID id,
            @Valid @RequestBody UpdateRoomRequest request) {
        return ResponseEntity.ok(roomService.updateRoom(id, request));
    }

    @Operation(summary = "Delete a room")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteRoom(@Parameter(description = "Room id") @PathVariable UUID id) {
        roomService.deleteRoom(id);
        return ResponseEntity.noContent().build();
    }
}

