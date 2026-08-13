package com.naydiko.backend.service;

import com.naydiko.backend.domain.entity.Level;
import com.naydiko.backend.domain.entity.Room;
import com.naydiko.backend.domain.repository.LevelRepository;
import com.naydiko.backend.domain.repository.RoomRepository;
import com.naydiko.backend.dto.request.CreateRoomRequest;
import com.naydiko.backend.dto.request.UpdateRoomRequest;
import com.naydiko.backend.dto.response.RoomResponse;
import com.naydiko.backend.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Business logic for managing {@link Room}s within a {@link Level}.
 */
@Service
@Transactional(readOnly = true)
public class RoomService {

    private final RoomRepository roomRepository;
    private final LevelRepository levelRepository;

    public RoomService(RoomRepository roomRepository, LevelRepository levelRepository) {
        this.roomRepository = roomRepository;
        this.levelRepository = levelRepository;
    }

    @Transactional
    public RoomResponse createRoom(CreateRoomRequest request) {
        Level level = findLevelOrThrow(request.levelId());

        Room room = Room.builder()
                .level(level)
                .name(request.name())
                .type(request.type())
                .floorFinish(request.floorFinish())
                .wallFinish(request.wallFinish())
                .ceilingFinish(request.ceilingFinish())
                .ceilingType(request.ceilingType())
                .ceilingHeightMm(request.ceilingHeightMm())
                .build();

        return toResponse(roomRepository.save(room));
    }

    @Transactional
    public RoomResponse updateRoom(UUID id, UpdateRoomRequest request) {
        Room room = findRoomOrThrow(id);

        room.setName(request.name());
        room.setType(request.type());
        room.setFloorFinish(request.floorFinish());
        room.setWallFinish(request.wallFinish());
        room.setCeilingFinish(request.ceilingFinish());
        room.setCeilingType(request.ceilingType());
        room.setCeilingHeightMm(request.ceilingHeightMm());

        return toResponse(room);
    }

    public RoomResponse getRoom(UUID id) {
        return toResponse(findRoomOrThrow(id));
    }

    public List<RoomResponse> listRoomsByLevel(UUID levelId) {
        return roomRepository.findByLevelId(levelId).stream()
                .map(RoomService::toResponse)
                .toList();
    }

    @Transactional
    public void deleteRoom(UUID id) {
        Room room = findRoomOrThrow(id);
        roomRepository.delete(room);
    }

    private Room findRoomOrThrow(UUID id) {
        return roomRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Room not found: " + id));
    }

    private Level findLevelOrThrow(UUID levelId) {
        return levelRepository.findById(levelId)
                .orElseThrow(() -> new ResourceNotFoundException("Level not found: " + levelId));
    }

    private static RoomResponse toResponse(Room room) {
        return new RoomResponse(
                room.getId(),
                room.getLevel().getId(),
                room.getName(),
                room.getType(),
                room.getFloorFinish(),
                room.getWallFinish(),
                room.getCeilingFinish(),
                room.getCeilingType(),
                room.getCeilingHeightMm(),
                room.getCreatedAt(),
                room.getUpdatedAt()
        );
    }
}

